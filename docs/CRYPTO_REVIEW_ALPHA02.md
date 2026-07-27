# Revisione crittografica alpha02

Questa è una revisione interna per ispezione e test, non un audit indipendente.

## Riepilogo

| Area | Meccanismo | Verifica | Limite |
|---|---|---|---|
| archivio | AES-256-GCM, chiave Android Keystore | ispezione + build | test Keystore su device pendente |
| PIN | PBKDF2-HMAC-SHA256, sale casuale | test JVM/ispezione | non deriva la chiave archivio |
| identità | ECIES P-256 + ECDSA P-256 Tink | round-trip e tamper test | niente forward secrecy |
| inviti | carta firmata, scadenza, nonce | test firma/Tor | non monouso |
| frame | v2, firma, nonce, timestamp | test handshake alterato | niente sessione ratchet |
| replay | ID persistiti e finestra limitata | test automatico | cronologia limitata a 10.000 |
| ricevute | ECDSA sul message ID | ispezione/test round-trip | metadati temporali |
| vocali | envelope cifrati, blocchi, SHA-256 | test frammentazione/ripresa | temporanei flash |
| gruppi | cifratura pairwise | test policy | non MLS |
| onion key | dentro stato cifrato/no-backup | ispezione | device compromesso |

## Android Keystore e archivio

`SecureStateStore` crea una chiave AES-256 non esportabile in
`AndroidKeyStore`, cifra JSON con AES/GCM/NoPadding e IV casuale generato
dall’implementazione. Il ciphertext include versione/IV e viene scritto nel sandbox
privato; backup e data extraction sono disabilitati.

**Verificato per ispezione:** algoritmo, dimensione chiave, GCM, backup escluso e
cancellazione della chiave durante Elimina identità.

**Da verificare su dispositivo:** invalidazione chiave, upgrade Android/app, crash durante
scrittura, hardware-backed/StrongBox, rollback file e comportamento dopo ripristino.

## PIN e PBKDF2

Il PIN è derivato con `PBKDF2WithHmacSHA256`, sale casuale di 16 byte e 210.000 iterazioni.
Il confronto usa `MessageDigest.isEqual`. Il sale non è segreto. I `CharArray` ricevuti dal
repository vengono azzerati dopo l’uso.

Il PIN non deriva una chiave distinta per l’archivio e non viene passato al Keystore:
serve come blocco applicativo. Un PIN breve resta vulnerabile a tentativi locali se un
attaccante ottiene sale/hash; non esiste rate limit persistente del PIN.

**Da verificare su dispositivo:** latenza su Android 9 di fascia bassa, UX, throttling e
protezione da PIN deboli. Non è stata sostituita PBKDF2 perché algoritmo/parametri sono
ragionevoli per alpha, ma serve benchmark.

## Identità, firma e cifratura

Tink genera:

- ECIES P-256 HKDF-HMAC-SHA256 + AES-128-GCM per cifratura ibrida;
- ECDSA P-256 SHA-256 DER per firme.

Chiavi pubbliche determinano ID/impronta; chiavi private restano nello stato cifrato.
Envelope lega versione, ID, mittente, destinatario, data e ciphertext. La frame esterna
lega inoltre nonce e timestamp. Le verifiche falliscono su ciphertext/nonce modificati.

**Limite critico:** cifratura alla chiave identità statica, senza X3DH/Double Ratchet o
equivalente. La compromissione della chiave privata può esporre ciphertext registrati.
Questa scelta richiede audit/protocollo di sessione prima di una beta.

## Inviti ed endpoint

La carta firmata contiene chiavi pubbliche, fingerprint, LAN, onion v3, porta, revisione,
scadenza e nonce. Il parser impone limiti, coerenza ID/fingerprint e firma. Revisioni Tor
in rollback o cambio onion alla stessa revisione vengono rifiutati.

L’invito non è monouso. La revoca è locale; la rotazione incrementa la revisione ma il nuovo
invito deve raggiungere il contatto fuori banda.

## Ricezione, replay e DoS

Prima della decifratura il mittente deve essere un contatto noto e non bloccato. Il server
limita frame, ricevute, connessioni, tempo e frequenza. Dopo la decifratura
`PayloadValidator` limita corpo, ID, gruppi, MIME, allegati, durata e hash.

La deduplicazione usa l’ID dell’elemento di coda; i vocali usano un ID logico separato.
Blocchi e replay vengono persistiti prima della ricevuta. La cronologia è limitata a
10.000 ID: un contatto autorizzato può tentare consumo di spazio/CPU entro i limiti.

## Vocali e gruppi

Ogni blocco vocale è dentro un envelope cifrato e firmato. I metadati devono essere uguali
fra blocchi, il totale è massimo 1,5 MB e la ricostruzione verifica SHA-256 con confronto
costante. I blocchi parziali sono nello stato cifrato e scadono dopo sette giorni.

Nei gruppi ogni destinatario riceve una cifratura separata. La rimozione impedisce nuove
code e una revisione vecchia viene rifiutata, ma non revoca contenuti precedenti.

## Randomness, cancellazione e log

ID usano UUID casuali; nonce e sali usano `SecureRandom`; Tink/Keystore generano chiavi e
IV. Byte audio in memoria vengono azzerati dove pratico. File temporanei vengono rimossi
in `finally`, annullamento, fine playback e inizializzazione successiva.

Nessun logger applicativo registra contenuti, chiavi, PIN, onion o frame. Gli errori UI
sono nomi di classe/etichette limitate. Android/Tor nativo può produrre log di sistema:
va controllato su una build release fisica.

## Classificazione finale

### Verificato automaticamente

- inviti/endpoint Tor firmati;
- cifratura round-trip e rifiuto tamper;
- handshake alterato;
- replay, retry/backoff, migrazione;
- fallback frame unica;
- vocali a blocchi, integrità, ripresa serializzata e gruppi.

### Verificato mediante ispezione

- Keystore/AES-GCM, backup disabilitato;
- parametri PBKDF2 e confronto;
- limiti socket/payload;
- onion key nell’archivio cifrato;
- assenza di log applicativi sensibili.

### Da verificare su dispositivo

- Keystore reale e latenza PBKDF2;
- Tor/onion, audio, temporanei dopo crash;
- foreground/WorkManager/boot;
- assenza di log sensibili del runtime.

### Da sottoporre ad audit esterno

- protocollo ECIES/ECDSA e canonicalizzazione;
- assenza di forward secrecy;
- replay/DoS sotto carico;
- gruppi e revoca;
- supply chain Tor/Tink;
- cancellazione e lifecycle Android.
