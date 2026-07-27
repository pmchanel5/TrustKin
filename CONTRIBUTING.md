# Contribuire

1. apri un’issue per modifiche di protocollo, crittografia o formato dati;
2. mantieni dipendenze minime e solo da repository ufficiali;
3. non aggiungere telemetria, backend obbligatori o segreti;
4. aggiungi test per ogni modifica di serializzazione, sicurezza o coda;
5. esegui `gradlew testDebugUnitTest lintDebug assembleDebug`;
6. aggiorna threat model, dipendenze e limiti quando cambia il comportamento.

Le pull request crittografiche richiedono una descrizione delle primitive, fonti e
compatibilità. Non proporre algoritmi inventati o codice da tutorial.
