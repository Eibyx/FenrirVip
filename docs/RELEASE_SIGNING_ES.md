# Firma de FenrirVip y actualizaciones

## Investigación del original

Se revisó el árbol público `main` de [umerov1999/Fenrir-for-VK](https://github.com/umerov1999/Fenrir-for-VK/tree/c8a1c997c3b97c8b93b6178a513d7ed3a41bc74c), commit `c8a1c997c3b97c8b93b6178a513d7ed3a41bc74c`. Su `app_fenrir/build.gradle.kts` no configura `signingConfigs`, almacén, alias ni contraseñas. No hay keystores ni workflows públicos de firma en el árbol revisado. Su README, instrucciones de compilación, paso 1, pide generar un certificado propio con `keytool`; no ofrece la identidad privada del autor. No se conoce su procedimiento privado de firma.

Se descargaron dos APK oficiales y se verificaron con Android Build Tools 37.0.0 `apksigner verify --verbose --print-certs`:

- [Fenrir_VK.apk, release 127.1](https://github.com/umerov1999/Fenrir-for-VK/releases/tag/127.1).
- [Fenrir_VK.apk, release 127](https://github.com/umerov1999/Fenrir-for-VK/releases/tag/127).

Ambos presentan el mismo certificado público:

```text
DN: CN=Fenrir VK, OU=Home, O=Fenrir, L=Moscow, ST=Moscow, C=RU
RSA: 2048 bits
SHA-256: 9c67df461bd174d2c7fd67756835b1702a7be9a1c68af98fa084ff818340bc8d
SHA-1: b4c4d25896ef9c3ac56ecfbc67ab06f484d8c9f5
```

El APK 127.1 verificó mediante el esquema v2. Su SHA-256 de archivo es `83183e7fe1139c85013736a1b9b54407e9971cb00319ea713c32f16552e9b3b8`; su paquete es `dev.ragnarok.fenrir`, versionCode 1000 y versionName 127, aunque el tag sea 127.1. La huella del archivo y la del certificado son datos distintos.

El certificado público permite identificar al firmante, pero no crear firmas. No existe acceso ni autorización demostrada a su clave privada. Tener autorización sobre el fork o una licencia del código fuente no concede acceso a la identidad privada del original. No se intentó extraer claves de APKs, consultar secretos ajenos ni contactar con el propietario.

`VK_API_APP_ID`/`VK_CLIENT_SECRET` identifican aplicaciones OAuth de VK. El archivo público `other_tool/VK-Signature-Generator.zip` describe firmas de peticiones de API. Ninguno contiene una clave Android utilizable para firmar APKs.

## Identidad propia persistente

Se generó **una sola vez**, fuera del checkout, una identidad independiente:

```text
DN: CN=FenrirVip Release, O=FenrirVip
RSA: 4096 bits; certificado SHA256withRSA; validez 10000 días
SHA-256: ca6ae8f6365d910fbc29424ead8a4528caae11a06bb720eb3f40e0d4be5d032f
```

La huella pública se conserva en `.github/fenrir-release-certificate.sha256`. CI comprueba la keystore antes de compilar y la firma final después. Una identidad distinta detiene la publicación. El workflow nunca genera claves Release. Cambiar el pin no crea una actualización compatible: requiere una decisión explícita de migración de identidad.

El material privado generado está únicamente en `/workspace/fenrirvip-signing`, directorio 700 y archivos 600, fuera de Git. No se publica ni se adjunta a artifacts. **Una carpeta del entorno cloud no sustituye una copia de seguridad duradera del propietario.** Antes de publicar, el propietario debe guardar ese directorio completo en almacenamiento privado seguro y configurar Secrets. No ejecutar `init` de nuevo: se debe reutilizar esta identidad. No compartir contraseñas, archivos privados ni tokens en chat, issues, logs o releases.

## Configurar Actions Secrets sin mostrar sus valores

La integración actual devuelve HTTP 403 `Resource not accessible by integration` al consultar `actions/secrets/public-key` y al listar secretos. El helper comprueba ese permiso antes de enviar datos y se detiene; **no ha creado ni cambiado Secrets**. El build anterior `40113784042` publicó un artifact `fenrir-modern-release-unsigned`, confirmando que no tenía una configuración de firma completa disponible en esa ejecución. No permite saber qué secretos individuales pudieran existir.

En una terminal del propietario con acceso seguro al directorio privado y a este checkout:

1. Guardar una copia privada segura del directorio completo. No usar Git, artifacts ni Releases para esa copia.
2. Autenticar `gh` como propietario con permiso de escritura de Actions Secrets, o conceder ese permiso explícitamente a la integración en GitHub. No intentar reutilizar la sesión denegada con métodos alternativos.
3. Ejecutar, desde el checkout:

```bash
python3 tools/release/signing.py check --directory /workspace/fenrirvip-signing
python3 tools/release/signing.py upload --directory /workspace/fenrirvip-signing
```

Si la carpeta se trasladó de forma privada a otro ordenador, usar su ruta real en ambos comandos. Se requiere JDK/keytool y GitHub CLI. En este entorno JDK 25 está en `/workspace/jdk25`; puede utilizarse con `JAVA_HOME=/workspace/jdk25` delante del comando. El helper valida el certificado, pasa los valores por entrada estándar a `gh secret set` y muestra únicamente nombres de secretos y la huella pública.

Se configuran estos cuatro Repository Actions Secrets:

| Nombre | Contenido, mantenido privado |
| --- | --- |
| `FENRIR_KEYSTORE_BASE64` | Keystore PKCS12 codificada en base64 |
| `FENRIR_KEYSTORE_PASSWORD` | Contraseña del almacén |
| `FENRIR_KEY_ALIAS` | Alias persistente |
| `FENRIR_KEY_PASSWORD` | Contraseña de la clave; igual a la del almacén en este PKCS12 |

Base64 no cifra ni protege una clave: se usa únicamente como formato para Secrets. No imprimirlo en terminal ni copiarlo al chat. Alternativamente, el propietario puede introducir estos valores mediante Settings → Secrets and variables → Actions, siempre desde los archivos privados y sin publicarlos.

Después, volver a ejecutar la última ejecución de **Fenrir Modern Release** en `fix/vk-topic-comments-pagination`. No es necesario fusionar a main. `workflow_dispatch` requiere que GitHub conozca el workflow en la rama predeterminada; el workflow también se ejecuta por push y una ejecución existente admite reejecución. Con credenciales incompletas falla; sin ninguna produce un artifact explícitamente unsigned y omite publicación. Solo una firma coincidente y todas las validaciones aprobadas habilitan la pre-release.

## Validaciones y sesiones

`verify-apk.py` comprueba integridad ZIP, alineamiento, firma real y pin, paquete `dev.ragnarok.fenrir`, versión del catálogo, español, ausencia de `debuggable`, las tres ABI configuradas y el mapping R8 con autenticación, paginación y ModernAppearance. CI adjunta el reporte, commit y hashes de fuentes. Tras la nueva investigación de SID, `verify-auth-apk.py` valida la identidad de fuentes embebida contra el commit y las clases DEX; la huella histórica de DirectAuthIdVKPresenter solo corresponde a 95309fc9. Es evidencia de compilación e inclusión; no sustituye probar login o LOAD MORE con VK real. La publicación está suspendida hasta esa validación.

La primera Release propia usa versionCode **1001**, versionName **127-modern.1**, y conserva el paquete. Futuras publicaciones deben incrementar versionCode y usar exactamente la misma keystore. R8 y el recorte de recursos permanecen activados. Las bibliotecas nativas Release utilizadas localmente provienen de la compilación fuente de `7e4c32ef`, ejecución `40113784042`; CI vuelve a compilarlas desde fuente.

`AccountsSettings` guarda tokens en SharedPreferences con claves `token<uid>`, guarda las cuentas registradas y las restaura en su inicialización (`loadAccounts(false)`). No se ha modificado este código, sus claves, el paquete ni el almacenamiento de sesiones. Una actualización Android normal aceptada, con paquete y firma compatibles, conserva los datos locales. No borrar almacenamiento, desinstalar ni cerrar sesión antes de actualizar.

Para comprobar antes de instalar:

```bash
adb shell pm path dev.ragnarok.fenrir
# Copiar la ruta base.apk obtenida, sin el prefijo package:
adb pull /ruta/obtenida/base.apk installed-fenrir.apk
apksigner verify --print-certs installed-fenrir.apk
apksigner verify --print-certs nueva-version.apk
# Solo si ambas firmas coinciden y la versión nueva es compatible:
adb install -r nueva-version.apk
```

La nueva firma propia **no puede actualizar** una instalación firmada por el desarrollador original ni los builds debug anteriores con certificados diferentes. Android rechazará esa operación; no desinstalar como solución si importa conservar sesiones. No se promete migrar tokens entre identidades. Para conservar una instalación oficial existente haría falta una actualización firmada legítimamente por su propietario. VK puede caducar/revocar tokens o imponer restricciones aunque los datos locales se conserven; no se eluden sus bloqueos.

No se han realizado pruebas en dispositivo de actualización, reapertura/restauración de sesión, login o paginación con VK real. La conservación descrita se fundamenta en el comportamiento de actualización Android y la revisión de almacenamiento; su comprobación completa necesita un dispositivo con sesión válida y dos APK de esta misma identidad.
