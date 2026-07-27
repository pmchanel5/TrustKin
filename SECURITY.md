# Sicurezza

Brotherhood `0.2.0-alpha02` è software sperimentale, non auditato e non adatto a
comunicazioni ad alto rischio. L’integrazione Tor compila ma non è stata verificata su un
dispositivo in questa sessione. I limiti sono in [docs/LIMITATIONS.md](docs/LIMITATIONS.md)
e la revisione delle primitive in
[docs/CRYPTO_REVIEW_ALPHA02.md](docs/CRYPTO_REVIEW_ALPHA02.md).

## Segnalazioni

Non pubblicare dettagli sfruttabili in una issue. Usa **Private vulnerability reporting**
del repository GitHub. Se non è disponibile, apri una issue senza riproduzione o dati
sensibili e chiedi un canale privato.

Indica versione, versione Android, impatto, prerequisiti e riproduzione minima. Non inviare
chiavi private, PIN, indirizzi onion attivi, messaggi, audio o archivi personali.

## Aspettative per una correzione

Una modifica di sicurezza deve conservare compatibilità con gli archivi esistenti oppure
documentare una migrazione, aggiungere un test di regressione, rieseguire lint e build,
aggiornare threat model e limiti, e non registrare contenuti o segreti. Le dipendenze
crittografiche e Tor non vanno aggiornate automaticamente senza verifica dei formati.
