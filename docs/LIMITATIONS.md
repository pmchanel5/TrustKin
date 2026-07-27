# Limiti reali di 0.2.0-alpha02

- Nessun emulatore o telefono era disponibile: Tor, LAN a due dispositivi, microfono,
  playback, boot e background OEM non sono verificati in esecuzione.
- Il runtime Tor e l’onion service v3 sono integrati nel codice, non certificati da un test
  end-to-end. La UI mostra esplicitamente “non verificato su dispositivo”.
- La cifratura ECIES statica non offre forward secrecy o post-compromise security.
- Il PIN protegge la UI ma non deriva né avvolge separatamente la chiave Keystore.
- Gli inviti scadono e sono firmati, ma non sono monouso. La revoca endpoint non si
  propaga: occorre rotazione e nuovo invito.
- L’indirizzo LAN firmato può diventare obsoleto dopo cambio rete; Tor resta il fallback.
- Alcune Wi-Fi isolano i client e impediscono LAN.
- La modalità Bilanciata usa il minimo intervallo WorkManager (15 minuti) e non promette
  ricezione immediata; produttori e risparmio energetico possono ritardarla ulteriormente.
- Sempre disponibile richiede notifica visibile, consuma più batteria e può essere
  terminata dal sistema o dall’utente.
- Il restart `START_STICKY` e il boot receiver non aggirano Force stop: Android richiede
  una nuova apertura dopo un arresto forzato.
- Su Android 9 il vocale usa AAC/MP4 anziché Opus/OGG.
- I vocali sono limitati a 60 secondi/1,5 MB e frammentati; le immagini restano in un unico
  envelope fino a 2,3 MB.
- La ripresa è a livello di blocchi confermati, non di byte all’interno del blocco.
- I gruppi usano cifratura pairwise e non MLS/Sender Keys.
- Rimuovere un membro non cancella dati già ricevuti.
- Nessuna biometria, messaggi temporanei, recupero account o backup cifrato.
- Nessun audit indipendente, test di penetrazione, SBOM firmata o build bit-for-bit
  riproducibile.
- L’eliminazione su flash è logica; non si promette cancellazione fisica.
