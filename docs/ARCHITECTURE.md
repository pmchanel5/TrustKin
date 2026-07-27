# Architettura

## Forma attuale

Per ottenere rapidamente una build verificabile il progetto usa un singolo modulo Gradle
Android, mantenendo separazioni di package che potranno diventare moduli senza cambiare le
responsabilità:

```text
app/
  ui/          Jetpack Compose, navigazione e stato schermate
  model/       modelli serializzabili e stati di consegna
  crypto/      identità, inviti, cifratura, firma e ricevute
  storage/     archivio cifrato, Android Keystore e PIN
  data/        repository, code, contatti, gruppi e deduplicazione
  transport/   trasporto LAN diretto
  media/       normalizzazione e pulizia immagini
  core/        retry, replay e frammentazione file
```

Il vecchio `app/brotherhood.py` non viene incluso dal modulo Android.

## Flusso identità

1. Tink genera una chiave privata ECIES P-256 e una ECDSA P-256.
2. L’identificativo e l’impronta derivano dalle due chiavi pubbliche.
3. Le chiavi private vengono serializzate solo dentro lo stato cifrato.
4. Lo stato è cifrato AES-256-GCM con chiave non esportabile di Android Keystore.
5. Il PIN è conservato come PBKDF2-HMAC-SHA256 con sale casuale e blocca la UI.

Il PIN non è ancora un secondo fattore crittografico per la chiave di archivio: un processo
che compromette l’app sul telefono sbloccato può chiedere al Keystore di decifrare.

## Flusso messaggio LAN

1. Il repository salva messaggio e elemento di coda nell’archivio cifrato.
2. Il mittente cifra un payload con la chiave pubblica ECIES del destinatario.
3. Firma intestazione e ciphertext con ECDSA.
4. Il trasporto apre un socket TCP diretto all’endpoint contenuto nell’invito.
5. Il destinatario accetta solo identità già presenti tra i contatti.
6. Verifica firma, destinatario, limiti, data e ID anti-replay; poi decifra.
7. Salva il messaggio prima di restituire una ricevuta firmata.
8. Il mittente verifica la ricevuta e rimuove l’elemento dalla coda tecnica.

Il destinatario deve essere raggiungibile sulla stessa LAN e avere l’app aperta. Non c’è
fallback via Internet in questa build.

## Gruppi

Il gruppo contiene massimo 20 ID. Ogni messaggio viene cifrato e inviato separatamente a ogni
membro. Nome, lista membri e revisione viaggiano dentro il payload cifrato. Dopo la rimozione,
la revisione aumenta e il membro rimosso non è più destinatario. Non esiste cancellazione
retroattiva e non viene implementato MLS/Sender Keys in questa fase.

## Immagini

L’immagine viene letta in memoria, orientata in base all’EXIF, ridimensionata, ricodificata
JPEG e quindi cifrata. La ricodifica rimuove EXIF e GPS. Non viene scritto un file temporaneo
in chiaro.

## Evoluzione modulare

Quando il trasporto Tor viene scelto, i package diventeranno almeno:
`core-identity`, `core-crypto`, `core-database`, `core-messaging`, `transport-tor`,
`transport-local`, `feature-contacts`, `feature-chat`, `feature-groups` e
`feature-settings`.
