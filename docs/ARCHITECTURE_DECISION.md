# Decisioni architetturali

## ADR-001 — Applicazione Android indipendente

**Stato:** accettata
**Data:** 27 luglio 2026

Il repository è stato migrato dal prototipo Python/browser a un APK Kotlin/Compose
indipendente, senza backend Brotherhood. Briar è stato analizzato al commit
`b46d008aceb4c9cea306df8299fcfc1b7ce79d21` (release 1.5.19), ma un fork completo o il
riuso di `bramble-*` avrebbe importato un grafo ampio e API non progettate come SDK.

La decisione resta:

- codice applicativo Brotherhood indipendente e GPLv3+;
- Tink per primitive locali/applicative;
- archivio locale cifrato;
- gruppi pairwise semplici;
- nessun servizio centrale.

Conseguenza accettata: il protocollo alpha è piccolo e ispezionabile, ma non eredita la
maturità del protocollo Briar e non offre forward secrecy.

## ADR-002 — Runtime Tor tramite Onion Wrapper

**Stato:** accettata per `0.2.0-alpha02`, verifica su dispositivo pendente
**Data:** 27 luglio 2026  
**Onion Wrapper:** 0.1.6
**Tor Android:** 0.4.9.11

### Contesto

Servivano SOCKS locale, onion service v3, ciclo start/stop, eventi di bootstrap,
persistenza della onion key, quattro ABI e compatibilità `minSdk 28`, senza implementare
Tor manualmente né dipendere da servizi proprietari.

### Alternative

| Opzione | Vantaggi | Costi/rischi |
|---|---|---|
| Fork Briar completo | protocolli e integrazione maturi | migrazione ampia, UI/DB/protocolli diversi |
| Moduli interni Briar | componenti collaudati | non sono SDK isolati, forte accoppiamento |
| Orbot esterno | aggiornamenti separati | dipendenza da app installata e IPC/UX esterni |
| Onion Wrapper + tor-android in-process | API piccola, onion v3, nessun servizio proprietario | APK più grande, responsabilità lifecycle e patch |
| Tor implementato in casa | nessuna dipendenza | non accettabile per sicurezza e manutenzione |

### Decisione

Usare `org.briarproject:onionwrapper-android:0.1.6` e i binari
`org.briarproject:tor-android:0.4.9.11`. Onion Wrapper è GPLv3 e compatibile con la licenza
del progetto; `tor-android` dichiara BSD-3-Clause nei metadati. Il wrapper espone stato,
bootstrap, padding, SOCKS/control port e onion service v3 con chiave riutilizzabile.

Il protocollo Brotherhood resta sopra Tor: identità autorizzate, doppia firma, nonce,
timestamp, replay, dimensioni, ricevute e coda non vengono delegati alla rete onion.

### Conseguenze

- il JAR Tor viene estratto in `jniLibs` generati per quattro ABI;
- onion key e indirizzo restano nell’archivio cifrato/no-backup;
- l’indirizzo non è mostrato nella diagnostica;
- l’APK cresce sensibilmente;
- il processo può consumare batteria e memoria in modalità persistente;
- dipendenze GPL/BSD sono registrate in `NOTICE.md`;
- il codice compila, ma l’assenza di dispositivo impedisce di dichiarare bootstrap,
  descriptor upload o scambio remoto verificati.

La decisione va riesaminata se Onion Wrapper non riceve patch compatibili, se Tor
0.4.9.11 presenta advisory non risolti o se un’integrazione ufficiale mantenuta offre un
percorso più sicuro.
