# Test

## Automatici

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
```

I test coprono:

- generazione identità, firma/verifica e round-trip cifrato;
- rifiuto del ciphertext modificato;
- serializzazione dei payload;
- replay, capienza della finestra e deduplicazione;
- retry esponenziale con limite;
- frammentazione, ricostruzione e integrità SHA-256.

I vecchi test Python restano eseguibili separatamente:

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

## Matrice manuale richiesta

Usare due emulatori o telefoni con dati applicazione separati:

1. creare due identità e scambiarsi gli inviti in entrambe le direzioni;
2. confrontare le impronte;
3. inviare testo con entrambi aperti sulla stessa LAN;
4. chiudere il destinatario, inviare, riaprirlo e attendere il retry;
5. interrompere e ripristinare Wi-Fi;
6. riavviare app e telefono e verificare persistenza;
7. inviare una foto con GPS/EXIF noto e verificare l’output;
8. creare un gruppo con tre identità e rimuovere un membro.

Non ancora verificato in questo ambiente: emulatori, telefoni fisici, riavvio dispositivo,
reti con client isolation e produttori Android diversi.
