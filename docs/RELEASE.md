# Release

1. eseguire test, lint e build da clone pulito;
2. aggiornare `versionCode`, `versionName` e `CHANGELOG.md`;
3. controllare dipendenze e avvisi di sicurezza;
4. firmare localmente l’APK release senza copiare il keystore nel repository;
5. verificare firma con `apksigner verify --verbose`;
6. generare SHA-256;
7. installare l’esatto APK su almeno due telefoni;
8. creare un tag firmato;
9. pubblicare APK, checksum, sorgenti e limiti noti su GitHub Releases.

La workflow su tag pubblica una build **debug chiaramente etichettata**, utile per test
pubblici ma non sostitutiva della release firmata localmente. Una release stabile richiede
un processo di firma e riproducibilità completato.
