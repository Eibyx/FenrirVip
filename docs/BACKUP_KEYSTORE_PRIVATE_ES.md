# Respaldo privado de la firma persistente de FenrirVip

El respaldo externo aún no está confirmado. El original protegido permanece en `/workspace/fenrirvip-signing` dentro de Codex. Publicar una APK no respalda su clave privada. No eliminar ni recrear el entorno hasta guardar y comprobar una copia fuera de Codex.

Esta guía no contiene claves ni contraseñas. Nunca adjuntar la carpeta a Git, GitHub Releases, artifacts, issues o chats. El certificado público o la APK por sí solos no permiten restaurar la clave de firma. No ejecutar `signing.py init` de nuevo.

## Copiar desde una terminal segura

La copia debe terminar en **tu ordenador o almacenamiento privado duradero**, no en otra ruta de `/workspace` o `/tmp`. Guarda la carpeta completa: `fenrirvip-release.p12`, `credentials.json`, `certificate.der` y `certificate.sha256`. El JSON contiene la contraseña: no abrirlo en una terminal grabada ni compartir su contenido.

Si tu terminal segura ya ofrece una conexión SSH autorizada al entorno, ejecuta lo siguiente **en tu ordenador**, sustituyendo `USUARIO@HOST_CODEX` por esa conexión real. Este documento no crea ni presupone acceso SSH a Codex.

```bash
umask 077
fenrir_backup_root="$HOME/RespaldoPrivado/FenrirVip"
mkdir -p "$fenrir_backup_root"
# Si ya existe esta carpeta de destino, detente y comprueba el respaldo anterior.
test ! -e "$fenrir_backup_root/fenrirvip-signing" || exit 1
scp -pr USUARIO@HOST_CODEX:/workspace/fenrirvip-signing "$fenrir_backup_root/"
chmod 700 "$fenrir_backup_root" "$fenrir_backup_root/fenrirvip-signing"
chmod 600 "$fenrir_backup_root/fenrirvip-signing/"*
```

Usa autenticación SSH y comprobación de la identidad del host normales; no desactives sus controles. Si tu terminal no ofrece SSH/SCP, utiliza su transferencia privada de archivos ya autorizada para copiar **la carpeta completa** al ordenador. No copies sus contenidos al chat. Mantén el disco o volumen de destino cifrado y conserva una segunda copia privada segura, por ejemplo en otra unidad cifrada; no guardes la contraseña del cifrado junto al archivo cifrado.

## Verificar la copia fuera de Codex

En ese ordenador, con Python, JDK/keytool y un checkout de FenrirVip que contenga `tools/release/signing.py`, ejecuta desde el checkout:

```bash
python3 tools/release/signing.py check \
  --directory "$HOME/RespaldoPrivado/FenrirVip/fenrirvip-signing"
```

El helper lee la contraseña localmente, valida permisos, abre el PKCS12 y compara el certificado con el pin público. Solo debe mostrar una confirmación y esta huella pública SHA-256:

```text
ca6ae8f6365d910fbc29424ead8a4528caae11a06bb720eb3f40e0d4be5d032f
```

Comprueba que los cuatro archivos se transfirieron y que el comando termina correctamente desde la copia externa. Conserva también el repositorio o el helper para poder verificar una restauración futura. La confirmación de respaldo debe indicar únicamente que se verificó fuera de Codex y la ubicación general privada, nunca archivos, contraseñas o tokens en el chat.

## Futuras compilaciones

Reutiliza este mismo PKCS12, alias y contraseña. Los Secrets de Actions, si los configura posteriormente el propietario autorizado, no sustituyen un respaldo recuperable de la carpeta. No regeneres la identidad en workflows. Mantén el paquete `dev.ragnarok.fenrir` y aumenta versionCode para nuevas versiones; no desinstales ni borres datos antes de una actualización compatible si quieres conservar sesiones. VK puede caducar sesiones independientemente de la firma.
