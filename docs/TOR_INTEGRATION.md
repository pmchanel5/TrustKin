# Integrazione Tor

## Stato verificato

| Elemento | Stato |
|---|---|
| risoluzione dipendenze e quattro ABI | compilato/verificato nella build |
| chiamate reali start/stop e observer Tor | implementate, non eseguite su dispositivo |
| generazione onion service v3 | implementata tramite `ADD_ONION`, non verificata |
| persistenza cifrata della onion key | implementata/ispezionata |
| connessione SOCKS a `.onion` senza DNS locale | implementata/ispezionata |
| consegna remota fra due reti | non verificata |
| comportamento su rete mobile/Doze/OEM | non verificato |

La UI non mostra “Tor verificato”: espone fase e bootstrap reali ricevuti dal wrapper e
mantiene l’avviso **Integrazione Tor compilata, non verificata su dispositivo**.

## Scelta

Dipendenze:

```text
org.briarproject:onionwrapper-android:0.1.6
org.briarproject:tor-android:0.4.9.11
```

Onion Wrapper è pubblicato dal Briar Project sotto GPLv3, supporta versioni Android
precedenti al `minSdk 28` di Brotherhood e fornisce:

- `AndroidTorWrapper`;
- ciclo start/stop e abilitazione rete;
- SOCKS/control port configurabili;
- observer per stato, percentuale bootstrap, clock skew e descriptor onion;
- onion service v3 con chiave nuova o esistente;
- connection padding.

Il runtime `tor-android` contiene `libtor.so` per armv7, arm64, x86 e x86_64 e dichiara
BSD-3-Clause nei metadati Maven. Non richiede Orbot installato, account, API key o servizio
proprietario. L’APK aumenta di dimensione e il progetto deve seguire gli advisory upstream.

Nell’ambiente locale non era disponibile una banca dati CVE aggiornata; non viene
dichiarata l’assenza di vulnerabilità. Prima di una distribuzione occorre riesaminare
versioni e advisory ufficiali.

## Avvio

1. vengono scelte porte locali libere per SOCKS e controllo;
2. Tor usa `noBackupFilesDir/tor-runtime`;
3. il wrapper avvia il binario adatto a `Build.SUPPORTED_ABIS`;
4. viene attivato connection padding;
5. `publishHiddenService(local=42337, remote=80, privateKey)` crea o ripristina un onion v3;
6. onion e private key vengono salvati nello stato già cifrato;
7. la rete Tor viene abilitata;
8. observer aggiorna fase, bootstrap e descriptor upload.

Un evento `CONNECTED` rappresenta lo stato autentico comunicato da Tor. La disponibilità
in ingresso è distinta da `onionServiceReady`, che diventa vera soltanto dopo l’upload del
descriptor corrispondente.

## Invio e ricezione

Per l’uscita viene creato un `Socket` con proxy SOCKS su `127.0.0.1`. L’indirizzo onion
usa `InetSocketAddress.createUnresolved`, evitando risoluzione DNS locale. Timeout:

- connessione Tor: 45 secondi;
- lettura: 45 secondi;
- ricevuta: massimo 32 KiB;
- frame applicativa: massimo 5 MB.

L’onion service inoltra la porta 80 alla stessa porta locale usata dal server LAN. Non
esiste una porta pubblica IP/Internet né UPnP.

## Autenticazione sopra Tor

Tor non sostituisce il protocollo:

- endpoint onion e revisione sono firmati nell’invito;
- frame versione 2 firmata con ID mittente/destinatario, nonce di 24 byte e timestamp;
- finestra temporale di 10 minuti per la frame;
- envelope cifrato/firma applicativa;
- rifiuto dei mittenti sconosciuti o bloccati prima della decifratura;
- replay persistito, validazione payload, rate limiting e massimo otto connessioni;
- ricevuta firmata prima di rimuovere il blocco dalla coda.

Il fallback LAN→Tor usa esattamente la stessa frame. Una consegna valida ferma il fallback.

## Endpoint, rotazione e revoca

L’indirizzo onion non viene mostrato nella diagnostica e non è pubblicato in directory.
La carta contatto include:

- onion v3;
- porta;
- revisione monotona.

Un update con revisione inferiore viene rifiutato. Un onion diverso alla stessa revisione
viene rifiutato. **Rigenera endpoint Tor** ferma Tor, elimina logicamente la vecchia onion
key, incrementa la revisione e crea un nuovo onion; l’utente deve condividere un nuovo
invito. Ogni contatto può anche disabilitare localmente l’endpoint Tor conosciuto.

Non esiste propagazione automatica di revoca o directory: è una scelta coerente con
l’assenza di backend, ma resta un limite operativo.

## Errori e log

Gli stati UI riportano soltanto classe tecnica/etichetta breve (`ClockSkew`, timeout,
tipo eccezione). Non vengono registrati onion, chiavi, frame, contenuti o percorsi.
In caso di errore di avvio il wrapper viene fermato e lo stato diventa `ERROR`, mai
simulato come connesso.

## Test d’integrazione richiesto

Eseguire il capitolo Tor di [TWO_DEVICE_TEST_PLAN.md](TWO_DEVICE_TEST_PLAN.md) su due
telefoni e reti diverse. Conservare versione, percentuale bootstrap, descriptor pronto,
tipo rete e checksum APK; non salvare onion completo. Solo dopo consegna testo/immagine/
vocale in entrambe le direzioni e ripresa offline si può dichiarare Tor verificato.
