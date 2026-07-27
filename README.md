# Brotherhood

Brotherhood è un’app Android sperimentale per messaggi privati peer-to-peer basati su
inviti. Non richiede numero di telefono, e-mail, rubrica, account centrale, server
Brotherhood, dominio, VPS o servizio a pagamento.

> **Stato di sicurezza:** `0.2.0-alpha02`, non sottoposta ad audit. Il codice integra un
> runtime Tor reale e crea onion service v3, ma in questo ambiente l’integrazione non è
> stata eseguita su emulatore o telefono. Non considerare Tor “verificato” e non usare
> questa alpha in situazioni ad alto rischio.

## Funzioni implementate

- identità locale con chiavi separate di cifratura e firma;
- archivio AES-256-GCM protetto da una chiave Android Keystore;
- blocco UI con PIN derivato tramite PBKDF2-HMAC-SHA256;
- inviti firmati via testo, link e QR, con endpoint LAN e onion v3 autenticati;
- trasporti intercambiabili: LAN diretta e Tor tramite onion service;
- selezione LAN → Tor, coda cifrata, retry, backoff, deduplicazione e ricevute firmate;
- messaggi testuali, immagini ricodificate senza EXIF e vocali fino a 60 secondi;
- vocali Opus/OGG su Android 10+ e AAC/MP4 su Android 9, divisi in blocchi da 64 KiB;
- ripresa dei blocchi mancanti dopo interruzione o riavvio;
- gruppi privati fino a 20 identità, con cifratura separata per destinatario;
- modalità **Sempre disponibile**, **Bilanciata** e **Solo quando aperta**;
- diagnostica locale priva di chiavi, contenuti e indirizzi onion;
- nessun tracker, analytics, Firebase, Supabase, Twilio o backend remoto.

Il vecchio prototipo Python/Cloudflare resta nella cartella `app/` come codice legacy, ma
non viene incluso nell’APK Android.

## Stato delle verifiche

La build JVM, i test unitari, la compilazione dei test strumentali, Android lint e gli APK
debug/release vengono verificati localmente e in CI. Non erano disponibili emulatori, AVD
o telefoni collegati: LAN fra due installazioni, bootstrap Tor, pubblicazione onion,
registrazione/riproduzione audio e comportamento OEM in background richiedono ancora il
piano su dispositivi.

Vedi:

- [audit alpha01](docs/ALPHA01_AUDIT.md);
- [integrazione Tor](docs/TOR_INTEGRATION.md);
- [comportamento in background](docs/BACKGROUND_BEHAVIOR.md);
- [piano su due dispositivi](docs/TWO_DEVICE_TEST_PLAN.md);
- [revisione crittografica](docs/CRYPTO_REVIEW_ALPHA02.md);
- [limiti reali](docs/LIMITATIONS.md).

## Requisiti e build

- Android 9 o successivo (`minSdk 28`);
- `targetSdk 35`;
- JDK 17;
- Android SDK Platform 35.

Windows:

```powershell
$env:JAVA_HOME = "C:\percorso\al\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Linux/macOS:

```bash
export JAVA_HOME=/percorso/al/jdk-17
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

L’APK debug è firmato esclusivamente con la chiave di sviluppo Android. L’APK release
prodotto dal repository è **non firmato** finché non viene applicata una firma locale
controllata. Istruzioni complete: [docs/BUILD.md](docs/BUILD.md) e
[docs/RELEASE.md](docs/RELEASE.md).

## Prova su due telefoni

Seguire [docs/TWO_DEVICE_TEST_PLAN.md](docs/TWO_DEVICE_TEST_PLAN.md). La schermata
**Impostazioni → Open source e diagnostica** mostra versione, tipo build, stato LAN/Tor,
coda, retry, ultimo errore tecnico e ID abbreviato; non mostra indirizzi onion o segreti.

## Modello di fiducia

Tor è solo un trasporto: ogni payload resta cifrato e firmato dal protocollo Brotherhood.
L’alpha non offre forward secrecy, post-compromise security, recupero dell’identità o
anonimato assoluto. Un telefono compromesso o un destinatario malevolo può leggere e
divulgare i messaggi in chiaro.

## Licenza

Brotherhood è distribuita sotto GNU GPLv3 o successiva. Il codice applicativo resta
un’implementazione indipendente; alpha02 usa però il wrapper Tor pubblicato dal progetto
Briar e il runtime `tor-android`, con le rispettive licenze. Vedi [NOTICE.md](NOTICE.md),
[docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) e [LICENSE](LICENSE).
