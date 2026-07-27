# Release

## Checklist alpha02

1. partire da un clone pulito e dal lock delle dipendenze versionato;
2. eseguire test JVM, compilazione test strumentali, lint, debug e release;
3. verificare che l’APK contenga le quattro ABI Tor previste;
4. rinominare gli artefatti locali:
   - `Brotherhood-0.2.0-alpha02-debug.apk`;
   - `Brotherhood-0.2.0-alpha02-release-unsigned.apk`;
5. verificare la firma debug e confermare che la release sia non firmata;
6. calcolare SHA-256 e salvare un file `SHA256SUMS.txt`;
7. leggere [LIMITATIONS.md](LIMITATIONS.md) e allegare le note di rilascio;
8. eseguire [TWO_DEVICE_TEST_PLAN.md](TWO_DEVICE_TEST_PLAN.md);
9. firmare localmente la release solo con il keystore di pubblicazione;
10. installare l’esatto APK firmato su almeno due telefoni;
11. creare un tag firmato e pubblicare APK, checksum, sorgenti e licenze.

## Automazione GitHub

La workflow ordinaria esegue build, test, lint, report dipendenze, verifica del lock,
checksum e caricamento di:

- APK debug;
- APK release **non firmato**;
- report test/lint;
- report dipendenze.

La workflow su tag pubblica entrambi gli artefatti con etichette esplicite. Nessuna chiave
privata viene memorizzata o richiesta da GitHub Actions. Una release non firmata non è
installabile finché non viene firmata; non deve essere chiamata
`Brotherhood-0.2.0-alpha02-release.apk`.

## Criterio di dichiarazione

In assenza del test su due dispositivi, alpha02 resta **implementata ma non verificata su
dispositivo**. La compilazione del runtime Tor non equivale a dimostrare bootstrap, onion
service o consegna remota.
