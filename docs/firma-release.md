# Firma del APK de release

El release se firma con un keystore propio si lo configuras; si no, cae a la clave de depuración y
Gradle muestra un aviso. La clave la genera cada persona: este repositorio no incluye ninguna y
`*.jks` y `*.keystore` están en `.gitignore`.

## Crear el keystore

Guárdalo fuera del repositorio (por ejemplo en `~/.config/friday/`):

```bash
mkdir -p ~/.config/friday
keytool -genkeypair -v -keystore ~/.config/friday/friday-release.jks \
  -alias friday -keyalg RSA -keysize 4096 -validity 10000
```

Anota las contraseñas en un gestor de contraseñas y haz copia del `.jks`: si lo pierdes no podrás
actualizar la app instalada sin desinstalarla.

## Configurarlo

Con variables de entorno:

```bash
export FRIDAY_KEYSTORE=/home/tu-usuario/.config/friday/friday-release.jks
export FRIDAY_KEYSTORE_PASSWORD=...
export FRIDAY_KEY_ALIAS=friday
export FRIDAY_KEY_PASSWORD=...        # opcional; por defecto la del keystore
```

O en `local.properties` (ignorado), con las mismas claves:

```properties
FRIDAY_KEYSTORE=/home/tu-usuario/.config/friday/friday-release.jks
FRIDAY_KEYSTORE_PASSWORD=...
FRIDAY_KEY_ALIAS=friday
```

## Construir y verificar

```bash
./gradlew assembleRelease
apksigner verify --print-certs app-android/build/outputs/apk/release/app-android-release.apk
sha256sum app-android/build/outputs/apk/release/app-android-release.apk
```

Cambiar de la clave debug a la tuya obliga a desinstalar la app una vez (la firma cambia).
Hazlo antes de configurar el reloj.
