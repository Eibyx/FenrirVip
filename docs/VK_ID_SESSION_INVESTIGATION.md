# Investigación de VK ID y 2FA

## Evidencia y límites

Base revisada: `95309fc9764d1ab14119e272a7b1f6779043b9c5`. Ese APK conserva la corrección anterior de DirectAuthIdVKPresenter, pero no disponemos de una traza del nuevo fallo ni de un dispositivo con sesión VK para reproducirlo. No se han realizado intentos de login reales ni se han consumido códigos. Las pruebas de estados usan respuestas simuladas, no VK. Esta corrección es una candidata para comprobar en dispositivo, no una declaración de que VK ya acepta el login.

Defectos comprobados en el código anterior:

1. `sendEcosystemOtp`, `checkEcosystemOtp`, `getEcosystemVerificationMethods` y `validateAccount` producen `ApiException` con `error_code` numérico. `isSidError` solo comprobaba `AuthException`, el tipo de `/oauth/token`. Un `sid is invalid` de ecosystem conservaba el estado rechazado; el siguiente clic repetía ese SID.
2. `currentSid` servía simultáneamente para validateAccount, sendOtp, checkOtp, el resultado confirmado, OAuth `validation_sid` y validatePhone. No existía propiedad por solicitud, generación de intento ni rechazo de callbacks anteriores. El código solo sustituía el SID si el resultado no estaba vacío; un checkOtp sin SID confirmado reutilizaba silenciosamente el SID de la solicitud anterior.
3. Tras `need_validation/2fa_libverify`, la pantalla ya había terminado de cargar mientras validatePhone se programaba con un retraso de un segundo. El usuario podía enviar el código antes de que llegase su nuevo SID. Respuestas tardías podían sobrescribir otro intento.
4. `doAuth` ponía `tokenNeedCode=false` ante cualquier error, incluso un código incorrecto. El siguiente clic podía omitir el código aunque la pantalla siguiera mostrando 2FA. La recuperación SID encadenaba llamadas que omitían SID o cambiaban grant, sin una propiedad explícita de cada etapa.
5. AuthApi repetía recursivamente respuestas `error_code=6`. Su captcha tenía sondeo sin límite y permitía otra repetición recursiva. Además, `directLogin` no comprobaba `LoginResponse.errorBasic`, aunque el adaptador sí lo deserializaba: un error OAuth con objeto `error` podía pasar como supuesto éxito sin token.
6. La construcción genérica de sufijos generaba `ecosystem.sendOtpCallreset`; el método correspondiente es `ecosystem.sendOtpCallReset`. No demuestra que este sea el fallo comunicado, pero sí un error del envío por llamada.
7. El logger HTTP BODY de Debug incluía cuerpos/cabeceras de autenticación. Release y Debug compartían la misma lógica de SID. Las reglas R8 existentes mantienen `dev.ragnarok.**`, excepciones, nombres y atributos; no encontramos una diferencia de serialización demostrada que explique el nuevo fallo. No se debilitaron esas reglas.

No puede determinarse todavía qué endpoint rechazó **el intento del usuario**, qué código exacto devolvió o si su sesión había expirado/consumido en el servidor. El diagnóstico nuevo permite obtener esa evidencia sin publicar credenciales.

## Solicitudes y propiedad de SID

`VkIdSession` administra las fases ACCOUNT, OTP, CONFIRMED y OAUTH_CHALLENGE, y entrega un objeto de solicitud con el SID capturado para esa operación. Una segunda operación no puede empezar mientras exista otra pendiente. Un callback solo se aplica si conserva identidad de solicitud y generación de intento. Cambiar credenciales durante una solicitud cancela sus trabajos e invalida esa generación.

- `auth.validateAccount`: inicia explícitamente un nuevo intento y captura el token anónimo válido. Las llamadas siguientes reutilizan ese mismo token mientras no haya expirado; no se mezcla un SID con un token anónimo actualizado por otro flujo.
- `ecosystem.getVerificationMethods` y `ecosystem.sendOtp{Sms,Push,Email,CallReset}`: usan el SID de la fase account/OTP de ese intento. Un SID nuevo de sendOtp sustituye al anterior; si sendOtp lo omite se conserva exactamente el SID enviado. No se presupone que la omisión signifique consumo.
- `ecosystem.checkOtp`: usa el SID vigente de account/OTP y el código introducido. Para el intercambio posterior se exige un SID confirmado explícito. Una respuesta incompleta no permite reutilizar el identificador potencialmente consumido.
- `/oauth/token`: conserva la selección de grants del cliente moderno: password para el método contraseña; phone_confirmation_sid cuando el resultado confirmado exige contraseña; without_password cuando no la exige. Se conserva el SID fresco de validateAccount/OTP en la entrada password existente; no se deduce el contrato del endpoint moderno a partir de ejemplos del endpoint legacy `/access_token`.
- `need_validation`: instala exclusivamente el `validation_sid` de OAuth, exige su presencia para 2FA y conserva el grant de esa solicitud. Un código rechazado no elimina la obligación de enviar código en el siguiente intento manual. Nunca se sustituye un validation_sid ausente por un SID de ecosystem.
- `auth.validatePhone`: usa el SID de OAuth. La pantalla y el bloqueo de solicitudes permanecen cargando hasta incorporar la respuesta. Se eliminó el retraso artificial anterior.

Los modelos de ecosystem no proporcionan una duración de SID documentada. No se inventa una caducidad de cinco horas ni se deduce una sesión válida a partir de su formato. Se invalidan los SID rechazados por VK, los resultados incompletos y los intercambios confirmados cuyo resultado de red es incierto. Un rechazo SID vuelve al inicio con un mensaje y **sin iniciar otra llamada automáticamente**.

Referencias públicas consultadas como contraste, no como documentación oficial ni integración real: [python273/vk_api](https://github.com/python273/vk_api/blob/ceaeca0ea9cfe72041a2d0d4afb9e7428ce2ef8d/vk_api/vk_api.py), cuyo checkOtp utiliza explícitamente el SID de su resultado; [SlvrTea/vk-music](https://github.com/SlvrTea/vk-music/blob/f8f73293b5447b507fea86dc5374f3442332cf8e/lib/data/provider/auth/auth_service.dart), con el intercambio confirmado moderno; y [PinoK ExchangeAuthApi](https://github.com/pinokio240/PinoK_1/blob/8ba9413f6ce2816bf5ea2a5dccbd92de80dfc643/app/src/main/java/re/pinok/auth/exchange/ExchangeAuthApi.kt), que separa password, confirmación y without_password en un endpoint legacy diferente. No se copian sus credenciales, dispositivos ni mecanismos de acceso.

## Restricciones temporales

Los códigos API 6/9/29, HTTP 429 y errores OAuth/textos de limitación conocidos detienen las solicitudes automáticas y muestran un mensaje específico EN/ES/RU. Si existe Retry-After numérico se respeta ese plazo. Sin un plazo explícito no se inventa una hora de desbloqueo: se bloquean nuevas solicitudes en ese diálogo y se indica esperar a VK. No se cambian IP, dispositivo, credenciales ni rutas para evitar el bloqueo.

El sondeo de la captcha existente se limita a 120 comprobaciones y su repetición de petición a una; no se ha eliminado la captcha. Las operaciones de login/SID no se repiten automáticamente ante errores transitorios o SID inválidos.

## Diagnóstico seguro e identidad del APK

Logcat usa exclusivamente el tag `FenrirAuth`. Se registran: commit abreviado, Debug/Release, método fijo y método de entrega permitido, evento, código API numérico, código OAuth de una lista fija, HTTP, clase de fallo, fase, generación, contador local de rotaciones y edad de la etapa. No se registran valores ni hashes de SID, tokens, contraseñas, teléfonos, correos, códigos, cuerpos, URLs ni Throwable/stacktrace. Códigos OAuth desconocidos se muestran como `other`.

Las rutas auth.*, ecosystem.*, token, get_anonym_token y auth_by_exchange_token eluden completamente el logger HTTP general, también en Debug y en peticiones de captcha repetidas. Las demás APIs mantienen su comportamiento.

El error visible muestra ese mismo diagnóstico seguro y se puede seleccionar/copiar. El título de login muestra versión y commit. Cada APK incluye `assets/fenrir-build.json`, con commit completo y hashes de ocho archivos de autenticación. `tools/release/verify-auth-apk.py` contrasta esos hashes contra Git y comprueba las clases dentro del DEX, además de ModernAppearance y el paginador. CI aplica esta verificación tanto a Debug como a Release y checkout de PR usa el SHA de su rama, no un merge implícito.

Para comprobar la instalación real, obtener la ruta con `adb shell pm path dev.ragnarok.fenrir`, copiar su base.apk con `adb pull`, y ejecutar:

```bash
python3 tools/release/verify-auth-apk.py installed-base.apk --expected-commit COMMIT_COMPLETO
sha256sum installed-base.apk
apksigner verify --verbose --print-certs installed-base.apk
```

El hash y el certificado deben corresponder al APK entregado. No se puede verificar el APK del dispositivo del usuario sin obtener esa evidencia; comprobar un APK de compilación no demuestra qué tiene instalado.

## Comprobaciones y publicación

La suite JVM compila las clases de sesión/política **de producción**, AuthException y NeedValidationException. Cubre rotación y omisiones de SID, 2FA con/sin contraseña, callbacks tardíos, envío concurrente de validatePhone, códigos incorrectos, resultado de intercambio incierto, captcha explícita, restricciones temporales, excepciones envueltas, privacidad del diagnóstico y nombres de endpoints. No ejecuta el Presenter Android ni realiza HTTP a VK. Se mantienen los tests de paginación y las fixtures de VKScript.

VersionCode 1002 / versionName 127-modern.2-auth-test identifican esta candidata. Se construyen Debug y Release con el SDK/NDK/CMake/R8 existentes. La Release local usa la misma identidad RSA4096; no se genera otra clave. Ni las sesiones persistentes existentes ni las funciones Modern Edition se alteran.

La publicación automática del workflow Release está suspendida. El borrador v127-modern.1 está marcado NO PUBLICAR; el publicador auxiliar anterior no se ha subido ni ejecutado. No se publicará una Release nueva hasta probar el login afectado con VK real, respetando cualquier restricción vigente. El respaldo privado externo de la keystore seguía pendiente de confirmación al empezar esta investigación.

Queda por verificar en dispositivo: método 2FA que realmente falló, primera etapa/código de error del diagnóstico, caducidad real de sesiones, login tras resolver captcha, conservación tras cierre/reapertura y comportamiento real de los grants modernos. Solo después de esos resultados se puede afirmar que el fallo del usuario está resuelto.
