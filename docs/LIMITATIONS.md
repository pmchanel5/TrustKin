# Limiti reali dell’alpha

- Tor e onion service v3 non sono integrati.
- Il trasporto reale funziona solo sulla stessa LAN e con l’app aperta.
- Endpoint e invito diventano obsoleti se cambia l’indirizzo IP; serve un nuovo invito.
- Alcune reti Wi-Fi isolano i client e impediscono il collegamento diretto.
- Non esistono modalità foreground “sempre disponibile” o scheduler bilanciato.
- La cifratura ECIES statica non offre forward secrecy o post-compromise security.
- Gli inviti scadono e sono firmati, ma non sono monouso né revocabili a distanza.
- I gruppi usano cifratura pairwise; non MLS/Sender Keys.
- Le immagini viaggiano in un singolo envelope dopo la ricodifica; il chunking è testato
  come logica ma non è ancora collegato al trasporto con ripresa.
- Messaggi vocali, biometria, messaggi temporanei e risposte non sono ancora collegati alla UI.
- La pulizia immagini e le migrazioni archivio richiedono test strumentati aggiuntivi.
- Nessun test su telefono o emulatore è stato eseguito in questo ambiente.
- Nessun audit indipendente e nessuna build riproducibile verificata.
- L’eliminazione su memoria flash è logica; non si promette cancellazione fisica.
