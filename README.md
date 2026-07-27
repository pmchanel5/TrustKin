# Brotherhood

Brotherhood è un’app Android sperimentale per comunicazioni private, peer-to-peer e basate
su inviti. Non richiede numero di telefono, e-mail, rubrica, account centrale, dominio,
VPS o servizio a pagamento.

> **Stato di sicurezza:** alpha di sviluppo, non sottoposta ad audit. La build attuale
> protegge chiavi e contenuti locali e cifra i pacchetti LAN, ma **non integra ancora Tor**
> e non offre ancora forward secrecy. Non usarla per situazioni ad alto rischio.

## Cosa funziona

- identità locale con chiavi separate per cifratura e firma;
- archivio cifrato tramite AES-GCM e Android Keystore;
- blocco dell’app con PIN derivato tramite PBKDF2-HMAC-SHA256;
- contatti tramite invito firmato, testo, link o QR;
- verifica manuale dell’impronta, nome locale, blocco e rimozione;
- chat testuale cifrata tra due telefoni sulla stessa rete locale;
- coda offline, retry esponenziale, deduplicazione e ricevute firmate;
- piccoli gruppi privati con cifratura separata per ogni destinatario;
- immagini corrette nell’orientamento, ridimensionate e ricodificate senza EXIF;
- nessun tracker, analytics, Firebase, Supabase, Twilio o backend Brotherhood.

Il vecchio prototipo Python/Cloudflare resta temporaneamente nella cartella `app/` come
codice legacy, ma non fa parte dell’APK Android e non rappresenta la nuova architettura.

## Requisiti Android

- `minSdk 28` — Android 9;
- `targetSdk 35` — Android 15;
- JDK 17;
- Android SDK Platform 35 e Build Tools 35.x.

Le versioni Android non sono ancora state provate su dispositivi fisici; la build e i test
JVM vengono verificati nel repository. Consulta [docs/TESTING.md](docs/TESTING.md).

## Compilazione

Su Windows:

```powershell
$env:JAVA_HOME = "C:\percorso\al\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

Su Linux/macOS:

```bash
export JAVA_HOME=/percorso/al/jdk-17
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

L’APK viene creato in `app/build/outputs/apk/debug/app-debug.apk`. Per installarlo:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Istruzioni complete: [docs/BUILD.md](docs/BUILD.md).

## Prova con due telefoni

1. Collega entrambi i telefoni alla stessa rete Wi-Fi.
2. Installa e apri Brotherhood su entrambi.
3. Crea un’identità e un PIN su ciascun telefono.
4. Sul primo telefono apri **Contatti → Mostra il mio invito**.
5. Sul secondo usa **Aggiungi → Scansiona QR**.
6. Ripeti nell’altra direzione, poi confronta le impronte.
7. Apri la chat e invia un messaggio.

Se il destinatario è offline o l’app è chiusa, il messaggio resta nella coda cifrata del
mittente e viene ritentato quando l’app è aperta. In questa alpha il servizio in background
non è attivo.

## Cosa non è

Brotherhood non è un social network: non esistono profili pubblici, ricerca globale,
follower, suggerimenti di contatti, pubblicità o caricamento della rubrica. Non promette
anonimato assoluto e non può proteggere da telefono compromesso, destinatario malevolo,
screenshot o analisi avanzata del traffico.

## Documentazione

- [Decisione architetturale](docs/ARCHITECTURE_DECISION.md)
- [Architettura](docs/ARCHITECTURE.md)
- [Threat model](docs/THREAT_MODEL.md)
- [Limiti reali](docs/LIMITATIONS.md)
- [Privacy](PRIVACY.md)
- [Segnalazioni di sicurezza](SECURITY.md)

## Licenza

Codice pubblicato sotto GNU General Public License v3 o successiva. Brotherhood è
un’implementazione indipendente: non contiene codice copiato da Briar. Vedi
[NOTICE.md](NOTICE.md) e [LICENSE](LICENSE).
