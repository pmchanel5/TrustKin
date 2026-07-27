# Comportamento in background

La modalità predefinita è **Bilanciata**. La scelta è salvata nell’archivio cifrato e
ripristinata dopo boot, salvo Force stop o restrizioni Android.

## Sempre disponibile

- avvia un foreground service reale e non esportato;
- chiama immediatamente `startForeground`;
- mostra una notifica persistente, silenziosa e riservata;
- notifica stato Tor e numero di elementi in coda;
- mantiene LAN e Tor mentre il servizio possiede il runtime;
- tenta la coda ogni 15 secondi;
- usa `START_STICKY` per un riavvio ragionevole del processo;
- offre l’azione **Arresta**, che seleziona Solo quando aperta.

Su Android 13+ il permesso notifiche viene chiesto soltanto quando l’utente seleziona
questa modalità. Il servizio dichiara `remoteMessaging` e le relative autorizzazioni
foreground. Consuma più batteria e dati; non è occultato e non aggira Doze o limiti OEM.

## Bilanciata

- nessun servizio persistente;
- WorkManager con vincolo rete connessa;
- lavoro periodico unico ogni 15 minuti, il minimo consentito da WorkManager;
- acquisisce LAN/Tor solo durante il lavoro;
- svuota la coda, può attendere fino a 90 secondi il bootstrap in corso e riprovare;
- dopo errori usa il retry WorkManager fino a tre tentativi dell’esecuzione.

Android può rinviare il lavoro per Doze, batteria, quote, produttore o rete. Questa modalità
non promette presenza continua né consegna immediata.

## Solo quando aperta

- cancella il lavoro periodico;
- ferma il servizio;
- LAN e Tor appartengono soltanto alla Activity visibile;
- all’uscita dall’app il proprietario UI viene rilasciato;
- minimo consumo, nessuna ricezione mentre l’app è chiusa.

## Eventi Android

| Evento | Comportamento previsto |
|---|---|
| boot completato | receiver non esportato rilegge la modalità e la configura |
| rete assente / modalità aereo | invio fallisce temporaneamente, elemento resta in coda |
| Wi-Fi → mobile | Tor riconnette secondo il wrapper; LAN può diventare non raggiungibile |
| revoca notifiche | Sempre disponibile può perdere visibilità utile; riselezionare la modalità |
| revoca microfono | non influenza rete; una nuova registrazione fallisce/chiede permesso |
| risparmio energetico | WorkManager/servizio possono essere ritardati o terminati |
| arresto manuale notifica | modalità salvata come Solo quando aperta |
| Force stop | Android impedisce receiver/service finché l’app non viene riaperta |
| aggiornamento app | stato schema migrato all’avvio; testare servizio e work dopo upgrade |

## Coordinamento

`TransportRuntimeController` conserva un insieme di proprietari. Il primo avvia LAN e Tor;
l’ultimo che esce li ferma. UI, worker e servizio non devono quindi spegnersi a vicenda
durante un passaggio foreground/background.

## Verifiche mancanti

Compilazione, manifest e test strumentale del servizio privato sono disponibili. Non sono
stati verificati in questa sessione notifica reale, boot, Doze, kill del processo, cambio
rete o comportamento dei produttori. Eseguire la matrice in
[TWO_DEVICE_TEST_PLAN.md](TWO_DEVICE_TEST_PLAN.md).
