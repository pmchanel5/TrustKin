# Privacy

Brotherhood non raccoglie, monetizza o invia al progetto dati personali. Non include
telemetria, analytics, pubblicità, tracker, crash reporter remoto, Firebase, rubrica,
posizione o identificatori pubblicitari.

Identità, chiavi, contatti, messaggi, gruppi, code, blocchi vocali incompleti e impostazioni
restano nell’archivio cifrato dell’app. I messaggi passano direttamente al destinatario via
LAN oppure attraverso la rete Tor. Non esiste un server Brotherhood.

Un invito condiviso esplicitamente contiene nome dichiarato, chiavi pubbliche, impronta,
endpoint LAN disponibile e indirizzo onion v3. L’onion è firmato dall’identità, non viene
pubblicato in directory e non è mostrato normalmente nella UI. Il destinatario dell’invito
può comunque conservarlo e condividerlo.

Il microfono viene usato solo dopo permesso Android e pressione prolungata del comando
vocale. Non viene registrato audio in background. Il file di registrazione e quello di
riproduzione sono temporanei nel sandbox privato e vengono rimossi al termine o
all’annullamento; la memoria flash non consente una garanzia di cancellazione fisica.

La modalità **Sempre disponibile** mantiene una notifica visibile e una connessione di rete.
La modalità **Bilanciata** usa esecuzioni periodiche Android e non promette ricezione
immediata. Android e il produttore del telefono possono osservare tempi d’uso, consumo,
rete e metadati del processo.

Tor riduce l’esposizione dell’IP fra contatti, ma non elimina dimensioni, tempi e correlazioni
del traffico. Guardie, rete locale, ISP, sistema operativo e un avversario globale possono
osservare metadati. Eliminare l’identità rimuove stato e chiave Keystore localmente, non le
copie già ricevute da altri.
