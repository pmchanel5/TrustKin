# Test

## Automatici JVM

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest
```

Copertura attuale:

- generazione identità, inviti firmati, endpoint Tor e invito Tor-only;
- cifratura/decifratura, firma, ciphertext alterato e handshake alterato;
- migrazione schema alpha01 → alpha02 e serializzazione dello stato;
- replay, capienza della finestra, retry e backoff;
- preferenza LAN → Tor, riuso della stessa frame e arresto dopo ricevuta valida;
- rate limiting per sorgente e globale;
- validazione di testo, immagini e vocali;
- frammentazione vocale, arrivo fuori ordine, SHA-256, gruppo e ripresa dopo
  serializzazione/riavvio simulato;
- regole di rimozione dei membri dai gruppi;
- selezione predefinita **Bilanciata**.

Questi sono test JVM. Non dimostrano che Tor, microfono, socket, Keystore o scheduler
funzionino su un telefono.

## Test strumentali

Compilazione senza dispositivo:

```powershell
.\gradlew.bat --no-daemon compileDebugAndroidTestKotlin
```

Esecuzione con dispositivo/emulatore:

```powershell
.\gradlew.bat --no-daemon connectedDebugAndroidTest
```

I test strumentali verificano:

- eliminazione di un file vocale temporaneo abbandonato;
- servizio foreground non esportato e tipo `remoteMessaging`.

Keystore, registrazione/riproduzione, boot, WorkManager e scambio Tor richiedono ulteriori
test d’integrazione su dispositivo. Vedi
[TWO_DEVICE_TEST_PLAN.md](TWO_DEVICE_TEST_PLAN.md).

## Lint e build

```powershell
.\gradlew.bat --no-daemon lintDebug assembleDebug assembleRelease
```

Un test superato non autorizza a dichiarare “Tor testato”: occorre osservare bootstrap,
descriptor onion e consegna fra due reti reali.

## Codice legacy

I test del prototipo Python sono separati dall’APK Android:

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Non costituiscono copertura della nuova architettura.
