# Brotherhood 0.2.0-alpha02

Seconda alpha Android: aggiunge trasporto Tor in-process con onion service v3, fallback
automatico LAN→Tor, vocali reali a blocchi con ripresa e tre modalità background.

## Artefatti

- `Brotherhood-0.2.0-alpha02-debug.apk`: firmato con chiave debug, solo test;
- `Brotherhood-0.2.0-alpha02-release-unsigned.apk`: ottimizzato ma non firmato, da firmare
  localmente prima dell’installazione;
- `SHA256SUMS.txt`: checksum degli esatti artefatti locali.

## Importante

Build e test automatici non sostituiscono il collaudo reale. In questa sessione non erano
disponibili emulatori o telefoni: Tor, LAN fra due device, microfono, playback e background
restano implementati ma non verificati su dispositivo. La UI lo segnala esplicitamente.

Software sperimentale non auditato, senza forward secrecy. Non usare in contesti ad alto
rischio. Leggere [LIMITATIONS.md](LIMITATIONS.md) e
[TWO_DEVICE_TEST_PLAN.md](TWO_DEVICE_TEST_PLAN.md).

## Installazione

Per il debug, abilitare installazione da sorgente attendibile e usare `adb install -r`.
Per la release, seguire [BUILD.md](BUILD.md) per firma e verifica con `apksigner`. Non
rinominare la release non firmata come release pronta.
