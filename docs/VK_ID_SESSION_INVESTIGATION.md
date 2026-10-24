# Investigación de VK ID y 2FA

## Evidencia y límites

Base revisada: `95309fc9764d1ab14119e272a7b1f6779043b9c5`. Ese APK conserva la corrección anterior de DirectAuthIdVKPresenter. Después se recibió una traza segura de la candidata `cf8ebeaa`: `method=auth.validateAccount api_code=14 oauth=other http=none kind=OTHER phase=EMPTY`. No hay un dispositivo con sesión VK disponible en este entorno. No se han realizado intentos de login reales ni se han consumido códigos. Las pruebas de estados usan respuestas simuladas, no VK. Esta corrección es una candidata para comprobar en dispositivo, no una declaración de que VK ya acepta el login.

Defectos comprobados en el código anterior:

1. `sendEcosystemOtp`, `checkEcosystemOtp`, `getEcosystemVerificationMethods` y `validateAccount` producen `ApiException` con `error_code` numérico. `isSidError` solo comprobaba `AuthException`, el tipo de `/oauth/token`. Un `sid is invalid` de ecosystem conservaba el estado rechazado; el siguiente clic repetía ese SID.
2. `currentSid` servía simultáneamente para validateAccount, sendOtp, checkOtp, el resultado confirmado, OAuth `validation_sid` y validatePhone. No existía propiedad por solicitud, generación de intento ni rechazo de callbacks anteriores. El código solo sustituía el SID si el resultado no estaba vacío; un checkOtp sin SID confirmado reutilizaba silenciosamente el SID de la solicitud anterior.
3. Tras `need_validation/2fa_libverify`, la pantalla ya había terminado de cargar mientras validatePhone se programaba con un retraso de un segundo. El usuario podía enviar el código antes de que llegase su nuevo SID. Respuestas tardías podían sobrescribir otro intento.
4. `doAuth` ponía `tokenNeedCode=false` ante cualquier error, incluso un código incorrecto. El siguiente clic podía omitir el código aunque la pantalla siguiera mostrando 2FA. La recuperación SID encadenaba llamadas que omitían SID o cambiaban grant, sin una propiedad explícita de cada etapa.
5. AuthApi repetía recursivamente respuestas `error_code=6`. Su captcha tenía sondeo sin límite y permitía otra repetición recursiva. Además, `directLogin` no comprobaba `LoginResponse.errorBasic`, aunque el adaptador sí lo deserializaba: un error OAuth con objeto `error` podía pasar como supuesto éxito sin token.
6. La construcción genérica de sufijos generaba `ecosystem.sendOtpCallreset`; el método correspondiente es `ecosystem.sendOtpCallReset`. No demuestra que este sea el fallo comunicado, pero sí un error del envío por llamada.
7. El logger HTTP BODY de Debug incluía cuerpos/cabeceras de autenticación. Release y Debug compartían la misma lógica de SID. Las reglas R8 existentes mantienen `dev.ragnarok.**`, excepciones, nombres y atributos; no encontramos una diferencia de serialización demostrada que explique el nuevo fallo. No se debilitaron esas reglas.

El diagnóstico aportado por el usuario identifica ahora **auth.validateAccount / API 14 (CAPTCHA requerida)** antes de contraseña/2FA. `ApiErrorCodes.CAPTCHA_NEED` y el localizador existentes confirman el significado del código. No es evidencia de un SID de autenticación caducado. El diagnóstico anterior no contenía los indicadores de presencia del desafío: no sabemos si VK devolvió `redirect_uri`, una CAPTCHA de imagen o datos incompletos. No se debe inferir el motivo por el que VK exige CAPTCHA ni un plazo de bloqueo a partir del código 14.

## Corrección de la clasificación de validateAccount (Modern.3)

En `cf8ebeaa`, `beginAccount()` mantiene la fase EMPTY durante la solicitud inicial; es correcto, porque todavía no existe un SID de autenticación. `session.failed()` puede dejar esa fase intacta o resetearla ante un fallo de transporte. El Presenter utilizaba `phase == EMPTY` como prueba de SID inválido, ocultando errores de red, HTTP, API y CAPTCHA. Una prueba ejecutada contra la condición anterior, extraída a la política de recuperación que utiliza ahora el Presenter, falló: esperaba reinicio manual de solicitud y recibía reinicio por sesión.

Ahora `AuthFailurePolicy.classify()` no recibe la fase: clasifica únicamente la causa. Distingue NETWORK, HTTP, API, AUTHENTICATION, SID_INVALID, MISSING_SESSION local, THROTTLED, INVALID_RESPONSE y CAPTCHA. `recovery()` usa EMPTY solo para decidir desde qué pantalla debe empezar un reintento manual; conserva la categoría y su mensaje. El código 14 se conserva literalmente en el diagnóstico y se clasifica CAPTCHA. Los números API no se sustituyen por códigos inventados; OAuth desconocido continúa redactado como `other`.

La petición inicial es POST a `https://<apiDomain>/method/auth.validateAccount`, con `lang`, `device_id`, `supported_ways=push,email,sms,callreset,password,reserve_code,codegen`, `login`, `force_password=0`, `passkey_supported=0`, `sak_version=20.0.0`, `flow_type=auth_without_password`, `v=5.186`, `https=1` y `api_id`. El proveedor mueve el token anónimo de `access_token` al header Authorization Bearer. **No envía `sid`, `validation_sid`, contraseña ni código 2FA.** No se cambian estas constantes ni se hacen solicitudes reales para adivinar el fallo.

Había un segundo defecto en el manejo de CAPTCHA de AuthApi: `handleError()` conservaba `handle=true` cuando faltaba `redirect_uri`, autorizando una repetición sin solución. Se corrige esa condición. Además validateAccount deja de usar el replay genérico: propaga el error a la UI una sola vez. Si API 14 trae redirect web, abre la CAPTCHA existente; si trae `captcha_sid` y `captcha_img`, muestra imagen y entrada de texto. Si no hay datos utilizables, informa de API 14 y no repite la petición. La candidata añade `captcha=WEB|IMAGE|UNAVAILABLE` y `request_sid_present=true|false` al diagnóstico; nunca sus valores. El evento ERROR se captura antes de liberar la solicitud: `busy=true` describe esa solicitud original, no indica un reintento.

Resolver el desafío solo guarda temporalmente la respuesta; el usuario debe pulsar Siguiente para enviar otra solicitud. El token web se envía como `success_token`, y el desafío de imagen como `captcha_sid` + `captcha_key`, separados de los SID de autenticación. Una respuesta CAPTCHA queda vinculada por identidad de solicitud al teléfono y token anónimo originales; un callback viejo no se aplica al desafío nuevo. Editar el teléfono cancela ese estado. Los datos no se guardan en preferencias, los campos de imagen no guardan estado y la imagen no se almacena en caché. El logger HTTP también omite `captcha.php`.

Las pruebas JVM reproducen la combinación reportada API 14/EMPTY y cubren errores normales, restricciones, códigos seguros, desafíos web/imagen/incompletos, callbacks tardíos y conservación del flujo OAuth. Ejecutan las clases de producción de política/sesión/desafío; no ejecutan el Presenter Android ni VK. Comprobación local de esta iteración: 72 pruebas JVM (31 de sesión, 16 de clasificación/recuperación, 5 de desafío y 20 de topics), 19 fixtures de VKScript y 1.534 recursos españoles; sin fallos. Se observó primero el fallo esperado de la regresión contra la condición antigua. La compilación Kotlin Release pasó y valida los enlaces del Presenter, diálogo, servicio y recursos; la APK completa y los workflows se comprueban por separado. Falta probar con la cuenta afectada qué desafío entrega VK, completarlo y verificar que se avanza a contraseña/2FA y al login.

## Solicitudes y propiedad de SID

`VkIdSession` administra las fases ACCOUNT, OTP, CONFIRMED y OAUTH_CHALLENGE, y entrega un objeto de solicitud con el SID capturado para esa operación. Una segunda operación no puede empezar mientras exista otra pendiente. Un callback solo se aplica si conserva identidad de solicitud y generación de intento. Cambiar credenciales durante una solicitud cancela sus trabajos e invalida esa generación.

- `auth.validateAccount`: inicia explícitamente un nuevo intento y captura el token anónimo válido. Las llamadas siguientes reutilizan ese mismo token mientras no haya expirado; no se mezcla un SID con un token anónimo actualizado por otro flujo.
- `ecosystem.getVerificationMethods` y `ecosystem.sendOtp{Sms,Push,Email,CallReset}`: usan el SID de la fase account/OTP de ese intento. Un SID nuevo de sendOtp sustituye al anterior; si sendOtp lo omite se conserva exactamente el SID enviado. No se presupone que la omisión signifique consumo.
- `ecosystem.checkOtp`: usa el SID vigente de account/OTP y el código introducido. Para el intercambio posterior se exige un SID confirmado explícito. Una respuesta incompleta no permite reutilizar el identificador potencialmente consumido.
- `/oauth/token`: conserva la selección de grants del cliente moderno: password para el método contraseña; phone_confirmation_sid cuando el resultado confirmado exige contraseña; without_password cuando no la exige. Se conserva el SID fresco de validateAccount/OTP en la entrada password existente; no se deduce el contrato del endpoint moderno a partir de ejemplos del endpoint legacy `/access_token`.
- `need_validation`: instala exclusivamente el `validation_sid` de OAuth, exige su presencia para 2FA y conserva el grant de esa solicitud. Un código rechazado no elimina la obligación de enviar código en el siguiente intento manual. Nunca se sustituye un validation_sid ausente por un SID de ecosystem.
- `auth.validatePhone`: usa el SID de OAuth. La pantalla y el bloqueo de solicitudes permanecen cargando hasta incorporar la respuesta. Se eliminó el retraso artificial anterior.

Los modelos de ecosystem no proporcionan una duración de SID documentada. En OAuth se conserva expired_at cuando aparece en la respuesta need_validation y se rechaza localmente un desafío que ya alcanzó ese plazo explícito; antes la excepción descartaba ese campo. Su semántica real en el endpoint privado todavía requiere comprobar una respuesta de VK, y no se asigna ese plazo a las etapas ecosystem. No se inventa una caducidad de cinco horas ni se deduce una sesión válida a partir de su formato. Se invalidan los SID rechazados por VK, los resultados incompletos y los intercambios confirmados cuyo resultado de red es incierto. Los SID ecosystem enviados a token no se reutilizan tras un rechazo o resultado incierto; la excepción es una captcha explícita pendiente de resolver. Un rechazo SID vuelve al inicio con un mensaje y **sin iniciar otra llamada automáticamente**.

Referencias públicas consultadas como contraste, no como documentación oficial ni integración real: [python273/vk_api](https://github.com/python273/vk_api/blob/ceaeca0ea9cfe72041a2d0d4afb9e7428ce2ef8d/vk_api/vk_api.py), cuyo checkOtp utiliza explícitamente el SID de su resultado; [SlvrTea/vk-music](https://github.com/SlvrTea/vk-music/blob/f8f73293b5447b507fea86dc5374f3442332cf8e/lib/data/provider/auth/auth_service.dart), con el intercambio confirmado moderno; y [PinoK ExchangeAuthApi](https://github.com/pinokio240/PinoK_1/blob/8ba9413f6ce2816bf5ea2a5dccbd92de80dfc643/app/src/main/java/re/pinok/auth/exchange/ExchangeAuthApi.kt), que separa password, confirmación y without_password en un endpoint legacy diferente. No se copian sus credenciales, dispositivos ni mecanismos de acceso.

## Restricciones temporales

Los códigos API 6/9/29, HTTP 429 y errores OAuth/textos de limitación conocidos detienen las solicitudes automáticas y muestran un mensaje específico EN/ES/RU. Si existe Retry-After numérico se respeta ese plazo. Sin un plazo explícito no se inventa una hora de desbloqueo: se bloquean nuevas solicitudes en ese diálogo y se indica esperar a VK. No se cambian IP, dispositivo, credenciales ni rutas para evitar el bloqueo.

El sondeo de la captcha existente se limita a 120 comprobaciones y su repetición de petición a una; no se ha eliminado la captcha. Las operaciones de login/SID no se repiten automáticamente ante errores transitorios o SID inválidos.

## Diagnóstico seguro e identidad del APK

Logcat usa exclusivamente el tag `FenrirAuth`. Se registran: commit abreviado, Debug/Release, método fijo y método de entrega permitido, evento, código API numérico, código OAuth de una lista fija, HTTP, clase de fallo, fase, generación, contador local de rotaciones y edad de la etapa. No se registran valores ni hashes de SID, tokens, contraseñas, teléfonos, correos, códigos, cuerpos, URLs ni Throwable/stacktrace. Códigos OAuth desconocidos se muestran como `other`.

Las rutas auth.*, ecosystem.*, token, get_anonym_token y auth_by_exchange_token eluden completamente el logger HTTP general, también en Debug y en peticiones de captcha repetidas. Las demás APIs mantienen su comportamiento.

El error visible muestra ese mismo diagnóstico seguro y se puede seleccionar/copiar. El título de login muestra versión y commit. Cada APK incluye `assets/fenrir-build.json`, con commit completo y hashes de quince archivos de autenticación. `tools/release/verify-auth-apk.py` contrasta esos hashes contra Git y comprueba las clases dentro del DEX, además de ModernAppearance y el paginador. CI aplica esta verificación tanto a Debug como a Release y checkout de PR usa el SHA de su rama, no un merge implícito.

Para comprobar la instalación real, obtener la ruta con `adb shell pm path dev.ragnarok.fenrir`, copiar su base.apk con `adb pull`, y ejecutar:

```bash
python3 tools/release/verify-auth-apk.py installed-base.apk --expected-commit COMMIT_COMPLETO
sha256sum installed-base.apk
apksigner verify --verbose --print-certs installed-base.apk
```

El hash y el certificado deben corresponder al APK entregado. No se puede verificar el APK del dispositivo del usuario sin obtener esa evidencia; comprobar un APK de compilación no demuestra qué tiene instalado.

## Comprobaciones y publicación

La suite JVM compila las clases de sesión/política **de producción**, AuthException y NeedValidationException. Cubre rotación y omisiones de SID, 2FA con/sin contraseña, callbacks tardíos, envío concurrente de validatePhone, códigos incorrectos, resultado de intercambio incierto, captcha explícita, restricciones temporales, excepciones envueltas, privacidad del diagnóstico y nombres de endpoints. No ejecuta el Presenter Android ni realiza HTTP a VK. Se mantienen los tests de paginación y las fixtures de VKScript.

VersionCode 1003 / versionName 127-modern.3-auth-test identifican esta candidata. Se construyen Debug y Release con el SDK/NDK/CMake/R8 existentes. La Release local usa la misma identidad RSA4096; no se genera otra clave. Ni las sesiones persistentes existentes ni las funciones Modern Edition se alteran.

La publicación automática del workflow Release está suspendida. El borrador v127-modern.1 permanece sin publicar. La candidata anterior `cf8ebeaa` se publicó como Auth Test por instrucción expresa del usuario; esta nueva candidata Modern.3 no se publica como Release durante esta investigación. Se mantienen las restricciones de VK y no se hacen intentos automáticos de login. El respaldo privado externo de la keystore seguía pendiente de confirmación al empezar esta investigación.

La primera etapa y código comunicados ya están identificados: validateAccount/API 14. Queda por verificar en dispositivo: tipo y resolución del desafío CAPTCHA, avance a contraseña/2FA, caducidad real de sesiones, login tras resolver captcha, conservación tras cierre/reapertura y comportamiento real de los grants modernos. Solo después de esos resultados se puede afirmar que el fallo del usuario está resuelto.
