# ADR-001 — Base tecnica Android e valutazione Briar

**Stato:** accettata per la Fase 0  
**Data:** 27 luglio 2026  
**Briar analizzato:** commit `b46d008aceb4c9cea306df8299fcfc1b7ce79d21`,
release 1.5.19.

## Contesto

Il repository Brotherhood conteneva un prototipo Python/browser con Cloudflare Quick
Tunnel, dati del gruppo sul computer host e nessuna cifratura end-to-end. La nuova richiesta
è un APK Android autonomo, senza server Brotherhood, con identità locale, inviti, Tor e
trasporti locali.

È stato esaminato il repository ufficiale di
[Briar](https://code.briarproject.org/briar/briar), incluso il codice Android, la licenza e
la configurazione di build. La release analizzata usa una struttura ampia composta da
`bramble-*`, `briar-*`, mailbox e test d’integrazione; dichiara `minSdk 21`, `targetSdk 35`,
Tor 0.4.9.11 e licenza GNU GPL v3 o successiva. Briar documenta connessioni dirette tramite
Tor, Wi-Fi e Bluetooth e un archivio locale cifrato.

## Opzioni

| Criterio | Fork completo Briar | Riuso moduli Briar | Implementazione indipendente |
|---|---|---|---|
| Sicurezza di trasporto | Migliore base già collaudata | Buona, se l’integrazione resta corretta | Incompleta finché Tor non viene integrato e auditato |
| Tempo per una UI Brotherhood compilabile | Alto: rinomina, rimozione forum/blog e migrazione UI | Alto: API interne molto accoppiate | Basso |
| Manutenzione | Richiede seguire un progetto grande e le patch upstream | Richiede seguire API non pensate come SDK | Codice piccolo, responsabilità diretta |
| Licenza | GPLv3+ obbligatoria, avvisi upstream | GPLv3+ e avvisi per i moduli riusati | Licenza scelta dal progetto; adottata GPLv3+ |
| Tor/onion service | Già presente | Possibile tramite `bramble-android`, ma non plug-and-play | Da integrare e verificare |
| Messaggi offline | Già presente; mailbox opzionale | Componenti disponibili ma interdipendenti | Coda mittente implementabile in modo semplice |
| Gruppi | Protocolli Briar maturi | Forte dipendenza dal modello Briar | MVP pairwise semplice, non definitivo |
| Immagini/vocali | Non allineato a tutti i requisiti Brotherhood | Richiede estensioni | Modellabili direttamente, con maggior lavoro |
| Dimensione APK | Elevata per Tor e stack completo | Elevata | Piccola prima di Tor |
| Android moderno/Compose | UI View esistente, Kotlin 1.9 | Adattatore Compose necessario | Compose e Material 3 nativi |

## Decisione

Brotherhood parte come **implementazione indipendente GPLv3+**, senza copiare codice Briar.
L’architettura di Briar viene usata come riferimento per la separazione tra identità,
sincronizzazione, trasporti e UI. Non si riusano moduli in questa fase perché non sono un SDK
isolato: trascinerebbero gran parte del grafo interno e renderebbero più lento ottenere una
build Brotherhood comprensibile.

La decisione privilegia una prima build installabile e ispezionabile. Ha però un costo
esplicito: il trasporto Tor e un protocollo con forward secrecy non possono essere dichiarati
completi. La build alpha implementa un trasporto LAN cifrato reale e mostra Tor come
“non integrato”.

Prima della milestone remota si deve riaprire questa ADR e scegliere una delle due strade:

1. integrare e mantenere i componenti Tor/Bramble rispettandone GPL e avvisi; oppure
2. integrare un runtime Tor Android ufficialmente mantenuto e progettare il protocollo di
   sessione con revisione esterna.

Non è ammesso presentare un socket SOCKS o una schermata “Tor” come onion service funzionante
senza test end-to-end su due dispositivi.

## Conseguenze

- nessun marchio, logo o codice Briar è incluso;
- `NOTICE.md` registra Briar soltanto come progetto studiato;
- il protocollo alpha usa Google Tink ECIES P-256 + AES-GCM e firme ECDSA P-256;
- l’archivio locale usa una chiave AES Android Keystore;
- i gruppi cifrano separatamente per ogni membro; rimuovere un membro esclude le consegne
  future ma non revoca dati già ricevuti;
- la release resta sperimentale finché Tor, forward secrecy, background mode, vocali e test
  fisici non sono completati.
