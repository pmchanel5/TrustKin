# Piano di test su due o tre dispositivi

## Preparazione

Usare almeno due telefoni Android 9+; per i gruppi servono tre identità, anche su un terzo
telefono o su un’installazione con dati separati. Annotare:

- modello/Android;
- checksum dell’APK;
- versione mostrata in diagnostica;
- ID identità abbreviato e impronta confrontata;
- rete e modalità background;
- esito, tempo e ultimo errore tecnico.

Non annotare PIN, chiavi, contenuti privati o onion completo. La build debug e una release
firmata possono coesistere solo se hanno application ID diversi; in caso contrario usare
telefoni/profili separati. Le identità si distinguono comunque da nome, impronta e ID breve.

## Identità e migrazione

- [ ] installare alpha01, creare identità/contatto, aggiornare ad alpha02 e verificare dati;
- [ ] creare Alice e Bob su due telefoni;
- [ ] scambiare QR in entrambe le direzioni;
- [ ] confrontare tutte le impronte fuori banda;
- [ ] copiare un invito a distanza e importarlo;
- [ ] verificare che l’onion non sia visibile nella UI normale;
- [ ] rimuovere Bob, verificare rifiuto dei messaggi, poi collegarlo con nuovo invito;
- [ ] rigenerare endpoint Tor di Alice, rifiutare update vecchio e importare quello nuovo;
- [ ] revocare localmente l’endpoint Tor di un contatto e verificare che resti solo LAN.

## LAN

Con entrambi sulla stessa Wi-Fi:

- [ ] stato LAN online e indirizzo coerente;
- [ ] testo in entrambe le direzioni;
- [ ] immagine con EXIF/GPS noto e verifica dell’assenza metadati nel ricevuto;
- [ ] vocale breve e vocale vicino a 60 secondi;
- [ ] play/pausa, avanzamento, cuffie e altoparlante;
- [ ] chiamata/interruzione audio durante registrazione;
- [ ] destinatario offline: coda, retry, riapertura e singola copia;
- [ ] chiusura app nelle tre modalità background;
- [ ] riavvio telefono e persistenza messaggi/coda;
- [ ] Wi-Fi con client isolation: fallimento controllato e fallback Tor;
- [ ] invio frame oltre limite/spam da harness autorizzato e verifica rifiuto/rate limit.

## Tor su reti differenti

Mettere Alice su Wi-Fi e Bob su rete mobile, senza percorso LAN:

- [ ] osservare bootstrap autentico 0→100 e descriptor onion pronto;
- [ ] confermare che la UI resta “non verificato” finché il piano non viene chiuso;
- [ ] testo in entrambe le direzioni;
- [ ] immagine;
- [ ] vocale multi-blocco;
- [ ] destinatario offline, poi ritorno e ripresa della coda;
- [ ] interrompere Tor/rete durante un vocale e verificare ripresa dei blocchi mancanti;
- [ ] cambiare Wi-Fi→mobile e mobile→Wi-Fi durante la sessione;
- [ ] modalità aereo e successivo recupero;
- [ ] riavviare app/processo e verificare riuso dello stesso onion;
- [ ] ruotare onion, condividere update firmato e verificare il nuovo endpoint;
- [ ] controllare che nessun DNS locale risolva `.onion`;
- [ ] controllare che non esista porta applicativa esposta sull’IP pubblico.

## Trasporto e deduplicazione

- [ ] con LAN e Tor disponibili, verificare che la diagnostica riporti LAN;
- [ ] rendere LAN non raggiungibile e verificare fallback Tor;
- [ ] interrompere una ricevuta dopo salvataggio sul destinatario;
- [ ] lasciare ritentare: deve comparire una sola copia;
- [ ] controllare che un elemento esca dalla coda solo dopo ricevuta firmata;
- [ ] superare il TTL in un ambiente con orologio controllato e osservare “scaduto”.

## Gruppi con tre identità

- [ ] Alice crea gruppo con Bob e Carlo;
- [ ] tutti vedono composizione e revisione;
- [ ] inviare testo, immagine e vocale;
- [ ] interrompere uno dei tre durante il vocale e verificare code indipendenti;
- [ ] Alice rimuove Carlo;
- [ ] inviare nuovi testo/immagine/vocale;
- [ ] Carlo non deve riceverli;
- [ ] Carlo prova a inviare con revisione vecchia: deve essere rifiutato;
- [ ] Bob prova a cambiare membri: deve essere rifiutato.

## Background e sistema

Per ogni telefono:

- [ ] Solo quando aperta: nessuna notifica/servizio dopo uscita;
- [ ] Bilanciata: nessuna promessa immediata, consegna al successivo lavoro;
- [ ] Sempre disponibile: notifica persistente, stato Tor e coda;
- [ ] arrestare dalla notifica e verificare modalità Solo quando aperta;
- [ ] riavvio con ognuna delle tre modalità;
- [ ] Doze e risparmio energetico;
- [ ] revoca/riassegnazione permesso notifiche;
- [ ] revoca/riassegnazione microfono;
- [ ] kill del processo, swipe task e Force stop, annotando differenze;
- [ ] aggiornamento alpha01→alpha02 e alpha02→nuova build.

## Criterio di superamento

Non dichiarare alpha02 verificata finché:

- entrambi i telefoni consegnano testo e vocale via Tor su reti diverse;
- descriptor onion e riuso della chiave sono osservati;
- LAN resta funzionante;
- fallback non crea duplicati;
- blocchi vocali riprendono dopo interruzione;
- le tre modalità rispettano la descrizione;
- nessun segreto appare nei log o nella diagnostica.
