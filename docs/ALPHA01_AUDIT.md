# Audit di Brotherhood 0.1.0-alpha01

**Commit esaminato:** `d3c0328 feat: bootstrap Android privacy messenger alpha`
**Data audit:** 27 luglio 2026
**Ambiente:** Windows, JDK 17.0.19, Android SDK 35, nessun emulatore o telefono collegato.

## Verifiche eseguite

Da una pulizia completa del progetto sono riusciti:

```text
testDebugUnitTest
lintDebug
assembleDebug
assembleRelease
```

L’APK debug è stato verificato con `apksigner`: firma Android debug valida con APK
Signature Scheme v2. SHA-256 della build di audit:
`E08729C2C55829B69EC0ADB6749297E11AA21B4CEB470F0157C75362BC0B0DDE`.

Queste prove verificano compilazione, test JVM, lint, packaging e firma. Non verificano il
comportamento su Android reale.

## Funzioni verificate nel codice

Mediante ispezione:

- generazione locale di keyset separati Tink ECIES P-256 ed ECDSA P-256;
- identificativo e impronta derivati dalle chiavi pubbliche;
- archivio JSON cifrato AES-256-GCM con chiave Android Keystore in `noBackupFilesDir`;
- PIN PBKDF2-HMAC-SHA256, 210.000 iterazioni, sale casuale di 128 bit e confronto
  `MessageDigest.isEqual`;
- invito con scadenza e firma dell’identità;
- importazione contatti, confronto ID/impronta, blocco e rimozione;
- socket TCP LAN con framing a lunghezza, timeout e limite massimo;
- rifiuto dei messaggi di identità non presenti tra i contatti;
- firma dell’envelope, cifratura per destinatario e ricevuta firmata;
- coda mittente, retry esponenziale, scadenza e deduplicazione;
- gruppi fino a 20 membri con cifratura separata per destinatario;
- ricodifica JPEG dopo correzione orientamento, senza riutilizzare i metadati EXIF;
- esclusione backup Android e `FLAG_SECURE`.

## Copertura automatica

Otto test JVM passano:

- 3 test crittografici: round-trip, manomissione e serializzazione;
- 5 test core: replay, backoff, chunking/integrità e regole di rimozione gruppi.

I test non coprono Android Keystore, persistenza, migrazioni, socket LAN, QR/camera,
ricodifica immagini Android, UI o ciclo di vita.

## Da verificare su dispositivi

- avvio, blocco/sblocco e cancellazione identità;
- persistenza dopo arresto forzato, aggiornamento e riavvio del telefono;
- scansione QR e permessi camera;
- comunicazione LAN tra due installazioni;
- reti Wi-Fi con client isolation, IPv4 multipli e cambio indirizzo;
- consegna dopo destinatario offline;
- pulizia EXIF/GPS su formati e produttori differenti;
- memoria e prestazioni con immagini vicine al limite;
- comportamento del Keystore con blocco schermo, aggiornamenti e ripristino;
- consumo batteria.

## Problemi e debito tecnico

1. `LanTransport` combina server in ingresso, client in uscita, discovery IP e accesso al
   repository; manca un’interfaccia comune per altri trasporti.
2. Non esistono rate limit per connessioni o richieste; il limite di dimensione evita payload
   illimitati ma non esaurimento tramite molte connessioni.
3. Il PIN è un gate dell’interfaccia: non deriva la chiave Keystore e la chiave AES non
   richiede autenticazione utente. Un processo compromesso nell’UID dell’app può chiedere la
   decifratura senza conoscere il PIN.
4. I keyset Tink privati sono serializzati in chiaro dentro il blob cifrato. Sono protetti a
   riposo, non quando lo stato è in memoria.
5. L’ECIES con chiave identità statica non offre forward secrecy né post-compromise security.
6. Non esiste un handshake di trasporto separato; l’autenticazione avviene verificando il
   primo envelope applicativo.
7. I contatti usano un solo endpoint LAN firmato nell’invito; cambio IP richiede un nuovo
   invito e non esiste revoca endpoint.
8. Immagini e allegati sono codificati Base64 nello stato e inviati in un singolo frame. La
   logica di chunking testata non è collegata al trasporto e non offre ripresa.
9. Non esiste una migrazione esplicita oltre ai valori predefiniti di Kotlin Serialization.
10. Il server usa una porta fissa e non limita connessioni simultanee.
11. Non esistono vocali, Tor, foreground service o WorkManager.
12. L’APK debug è grande soprattutto per tooling/UI e dipendenze; la variante release
    minificata compila ma è non firmata.

## Dipendenze critiche

- **Google Tink 1.16.0:** formato chiavi e primitive dei messaggi; un aggiornamento richiede
  prova di compatibilità con archivi esistenti.
- **Android Keystore:** radice della protezione a riposo, dipendente dal dispositivo.
- **Kotlin Serialization:** formato persistente e di rete.
- **ZXing / Android Embedded:** parser e fotocamera degli inviti.
- **ExifInterface:** interpretazione dell’orientamento prima della ricodifica.

Le dipendenze dirette sono fissate e le transitive sono nel lockfile.

## Differenze tra documentazione e implementazione

La documentazione alpha01 descrive correttamente Tor, vocali e background come mancanti.
“Chat LAN funzionante” significa che esiste un’implementazione compilata, non una verifica
end-to-end su due dispositivi. “Archivio protetto tramite PIN” sarebbe fuorviante: il blob è
protetto dal Keystore, mentre il PIN blocca la UI. Il README usa la formulazione più prudente
“blocco dell’app con PIN”, che corrisponde al codice.

## Conclusione

Alpha01 è una base Android compilabile con primitive reali e un trasporto LAN implementato,
ma la sicurezza e l’interoperabilità non sono state convalidate su dispositivi. Le priorità
per alpha02 sono: isolare il trasporto, aggiungere rate limit e test di protocollo, introdurre
migrazioni esplicite, integrare Tor senza stati simulati e ampliare i test Android.
