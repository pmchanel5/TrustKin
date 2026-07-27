# Threat model

## Cosa protegge l’alpha

- contenuto dei messaggi durante un collegamento LAN da osservatori passivi e modifiche;
- autenticità del mittente rispetto alla chiave salvata nel contatto;
- chiavi e cronologia a riposo contro la sola lettura dei file dell’app;
- replay dello stesso ID entro la cronologia locale conservata;
- scansione globale utenti: non esiste alcuna directory o backend;
- backup involontario: dati e chiavi sono esclusi dai backup Android.

## Cosa non protegge

- telefono sbloccato o compromesso, malware con privilegi, debug di processo;
- contatto malevolo, inoltri, foto dello schermo o un secondo dispositivo;
- analisi del traffico, indirizzi IP e relazioni sulla LAN;
- perdita del dispositivo o delle chiavi: non esiste recupero;
- vulnerabilità sconosciute, errori di implementazione o supply chain;
- anonimato di rete: Tor non è presente nella build alpha;
- forward secrecy e post-compromise security: ECIES usa una chiave identità statica;
- cancellazione fisica garantita su memoria flash.

## Attori

### Attaccante di rete

Può osservare, bloccare, ritardare, riprodurre e modificare pacchetti. Il contenuto e le
ricevute sono cifrati/autenticati, ma host, porta, dimensione e tempi dei collegamenti LAN
restano visibili. Può causare denial of service.

### Relay

Non esiste un relay Brotherhood. In questa alpha non esiste neppure un onion service. Una
futura integrazione Tor non sostituirà la cifratura applicativa.

### Contatto malevolo

Un contatto autorizzato può conservare, copiare, fotografare o divulgare ciò che riceve,
inviare payload ostili entro i limiti e tentare spam. Blocco, limiti, verifica delle firme e
validazione riducono l’impatto, non rendono affidabile il contatto.

### Telefono rubato

Il PIN blocca l’accesso normale all’interfaccia e il Keystore protegge la chiave a riposo.
Un PIN debole, una sessione già aperta o la compromissione del sistema possono esporre i
dati. La cancellazione remota non è offerta.

### Malware sul telefono

Malware con accessibilità, cattura schermo, root o capacità di iniezione può leggere
contenuti prima/dopo la cifratura. `FLAG_SECURE` riduce screenshot e anteprime ma non è una
garanzia contro un sistema compromesso.

### Perdita e compromissione delle chiavi

Non esiste backup o recupero. La compromissione della chiave statica può compromettere i
ciphertext registrati: manca forward secrecy. La rotazione completa identità/sessioni è un
requisito prima di una release stabile.

### Gruppi

Il messaggio viene cifrato per ogni membro corrente. Un membro rimosso non riceve le
consegne future, ma conserva legittimamente la cronologia e le chiavi già ottenute. La lista
membri ricevuta deve includere mittente e destinatario e non superare 20 elementi.

### Metadati e analisi del traffico

Nessun metadato viene caricato dal progetto, ma rete locale, sistema operativo e osservatori
possono vedere orari, volume e indirizzi. Tor dovrà ridurre l’esposizione degli indirizzi,
senza promettere protezione assoluta dall’analisi globale.

### Backup e screenshot

Il backup Android è disabilitato. Non esiste ancora backup cifrato esplicito. L’app imposta
`FLAG_SECURE`, ma un destinatario può usare un altro dispositivo per acquisire lo schermo.
