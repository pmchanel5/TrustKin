# Build Android

## Toolchain

- JDK 17;
- Gradle 8.9 tramite wrapper;
- Android Gradle Plugin 8.7.3;
- Kotlin 2.0.21;
- `compileSdk`/`targetSdk` 35, `minSdk` 28;
- Android SDK Platform 35 e Build Tools 35.x.

Installa Android Studio o i command-line tools. Configura `JAVA_HOME` e `ANDROID_HOME`;
`local.properties` è un’alternativa locale e non deve essere versionato.

## Verifica completa

Windows:

```powershell
$env:JAVA_HOME = "C:\percorso\al\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat --no-daemon clean testDebugUnitTest compileDebugAndroidTestKotlin `
  lintDebug assembleDebug assembleRelease
```

Linux/macOS:

```bash
export JAVA_HOME=/percorso/al/jdk-17
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew --no-daemon clean testDebugUnitTest compileDebugAndroidTestKotlin \
  lintDebug assembleDebug assembleRelease
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

L’APK include `libtor.so` per `armeabi-v7a`, `arm64-v8a`, `x86` e `x86_64`. Il task
`unpackTorBinaries` estrae il runtime Maven nell’albero generato di `jniLibs`; nessun
binario è copiato manualmente nel sorgente.

## Lock delle dipendenze

La build fallisce se una configurazione risolve componenti fuori da
`app/gradle.lockfile`. Dopo un aggiornamento intenzionale:

```powershell
.\gradlew.bat --no-daemon :app:dependencies --write-locks
git diff -- app/gradle.lockfile
```

Revisionare ogni differenza prima del commit. In CI viene eseguito il report delle
dipendenze e controllato che la build non modifichi il lock.

## Installazione debug

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

La build debug usa la chiave di sviluppo generata dall’Android SDK. Non è una firma di
rilascio e non va distribuita come build stabile.

## Firma release locale

Il repository produce deliberatamente `app-release-unsigned.apk`. Non aggiungere mai
keystore, password, variabili di firma o file `.properties` al repository.

```powershell
keytool -genkeypair -v -keystore brotherhood-release.jks -alias brotherhood `
  -keyalg RSA -keysize 4096 -validity 10000

& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" sign `
  --ks .\brotherhood-release.jks `
  --out .\Brotherhood-0.2.0-alpha02-release.apk `
  .\app\build\outputs\apk\release\app-release-unsigned.apk

& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose `
  .\Brotherhood-0.2.0-alpha02-release.apk
```

Conserva chiave e credenziali in luoghi cifrati distinti. Calcola il checksum solo dopo la
firma:

```powershell
Get-FileHash .\Brotherhood-0.2.0-alpha02-release.apk -Algorithm SHA256
```

## Riproducibilità

Le versioni dirette e transitive sono bloccate, ma APK bit-for-bit riproducibili non sono
ancora garantiti: R8, toolchain Android, timestamp e metadati ZIP devono essere confrontati
in almeno due ambienti puliti.
