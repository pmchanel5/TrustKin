# Changelog

## 0.2.0-alpha02 — 2026-07-27

- isolato il trasporto LAN dietro `MessageTransport`, mantenendo framing e protocollo;
- aggiunti limiti di connessione, rate limiting e validazione dei payload ricevuti;
- integrato `onionwrapper-android` con binari Tor 0.4.9.11 per quattro ABI;
- aggiunta creazione e persistenza cifrata di onion service v3;
- aggiunti endpoint Tor firmati, revisione anti-rollback, rotazione e revoca locale;
- aggiunto handshake di rete firmato con versione, nonce e finestra temporale;
- aggiunto router LAN → Tor con stessa coda, stessa frame e arresto alla ricevuta valida;
- aggiunti vocali reali con gesto pressione/rilascio/annulla, timer e permesso esplicito;
- aggiunti Opus/OGG su API 29+ e fallback AAC/MP4 su API 28;
- aggiunti blocchi vocali da 64 KiB, ricevute per blocco, integrità SHA-256 e ripresa;
- aggiunta riproduzione singola con play/pausa, avanzamento e pulizia dei temporanei;
- aggiunte modalità background Sempre disponibile, Bilanciata e Solo quando aperta;
- aggiunti foreground service visibile, WorkManager e ripristino dopo boot;
- aggiunta migrazione esplicita dello schema alpha01 → alpha02;
- aggiunti test per Tor firmato, handshake, fallback, migrazione, vocali e background;
- aggiornati workflow, documentazione, audit e piano di test su più dispositivi.

**Non verificato in questa sessione:** nessun emulatore o telefono era disponibile. Non
sono quindi dichiarati verificati il bootstrap Tor, l’onion service, lo scambio remoto,
l’audio reale, la LAN a due dispositivi e il comportamento background dei produttori.

## 0.1.0-alpha01 — 2026-07-27

- aggiunto progetto Android Kotlin/Compose compilabile;
- aggiunte identità locali, impronte, inviti firmati e QR;
- aggiunti archivio Keystore/AES-GCM e blocco PIN PBKDF2;
- aggiunti contatti, chat LAN cifrata, coda offline, retry e ricevute;
- aggiunti gruppi pairwise fino a 20 membri;
- aggiunta ricodifica immagini senza EXIF;
- aggiunti test e documentazione iniziali.
