# Build Android

## Versioni

- JDK 17;
- Gradle 8.9 tramite wrapper;
- Android Gradle Plugin 8.7.3;
- Kotlin 2.0.21;
- `compileSdk` e `targetSdk` 35;
- `minSdk` 28.

## Preparazione

Installa Android Studio o Android command-line tools, SDK Platform 35 e Build Tools 35.x.
Configura `JAVA_HOME` e `ANDROID_HOME`; in alternativa Android Studio genera localmente
`local.properties`. Questo file non va versionato.

## Comandi

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

L’APK debug è:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Calcolo checksum:

```powershell
Get-FileHash .\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

## Firma release locale

Non aggiungere mai keystore, password o file `.properties` di firma al repository.

```powershell
keytool -genkeypair -v -keystore brotherhood-release.jks -alias brotherhood `
  -keyalg RSA -keysize 4096 -validity 10000
```

Conserva keystore e password in due luoghi cifrati distinti. La configurazione automatica
della firma release non è presente nell’alpha: una release pubblica deve essere firmata
fuori dal repository e verificata prima dell’upload.

## Riproducibilità

Le versioni dirette sono bloccate, ma APK perfettamente riproducibili non sono ancora
garantiti: toolchain Android, R8 e metadati ZIP devono essere fissati e confrontati in due
ambienti puliti.
