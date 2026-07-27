# Threat model

## Proprietà offerte dall’alpha

- contenuti cifrati e firmati sopra LAN e Tor;
- autenticità rispetto alle chiavi già salvate nel contatto;
- endpoint onion firmato, revisionato e non pubblicato centralmente;
- chiavi e cronologia a riposo contro la sola lettura dei file;
- replay limitato da ID persistiti, nonce e finestra temporale;
- blocchi vocali limitati, verificati e ricostruiti con SHA-256;
- nessuna directory utenti, telemetria o server Brotherhood;
- backup Android disabilitato.

## Proprietà non offerte

- forward secrecy o post-compromise security: ECIES usa chiavi identità statiche;
- anonimato assoluto o resistenza garantita all’analisi globale;
- protezione da telefono sbloccato/compromesso, root o debug di processo;
- protezione dal destinatario che copia o registra il contenuto;
- recupero di identità, chiavi o messaggi;
- cancellazione fisica garantita su memoria flash;
- disponibilità contro DoS, censura Tor o restrizioni OEM;
- sicurezza dimostrata da audit indipendente.

## Attaccante di rete

Può osservare, bloccare, ritardare, riprodurre e modificare pacchetti. Firma e cifratura
proteggono contenuto/autenticità, mentre limiti, timeout e rate limiting riducono alcuni
DoS. In LAN restano visibili IP, porta, dimensione e tempi. Su Tor i contatti non vedono
direttamente l’IP, ma restano possibili correlazioni temporali e volumetriche.

## Relay e rete Tor

Non esiste un relay Brotherhood. Guardie, directory e relay Tor appartengono alla rete
Tor e possono osservare porzioni di metadati, non il payload applicativo in chiaro.
Compromissione o correlazione su più punti non è esclusa. La chiave onion è sensibile:
chi la ottiene può impersonare quell’endpoint, ma non può firmare frame Brotherhood senza
la chiave identità.

## Contatto autorizzato malevolo

Può inviare traffico valido, conservare/inoltrare messaggi, tentare spam e condividere
l’onion. Il ricevitore impone autenticazione, blocco, massimo otto connessioni, rate limit,
5 MB per frame, limiti di payload e massimo otto vocali parziali. Queste misure non rendono
affidabile il contatto.

## Telefono rubato o malware

Il PIN blocca la UI e il Keystore protegge la chiave a riposo. Il PIN non deriva la chiave
dell’archivio; un processo compromesso con accesso all’app può chiedere la decifratura.
`FLAG_SECURE` riduce screenshot e anteprime, ma accessibilità, root, injection, fotocamere
esterne e una sessione sbloccata restano fuori protezione.

## Inviti ed endpoint

Gli inviti scadono e sono firmati, ma non sono monouso. Chi riceve un invito può
redistribuirlo finché valido. Gli update Tor inferiori o equivoci alla stessa revisione
sono rifiutati. La revoca è locale e la rotazione richiede la consegna di un nuovo invito:
non esiste una lista di revoca centrale.

## Gruppi

Ogni membro riceve un ciphertext separato. Un rimosso non è destinatario delle nuove code,
ma conserva la cronologia già ricevuta. La composizione deve includere mittente e
destinatario, massimo 20 ID; soltanto il proprietario può aumentare una revisione nota.
Non esistono garanzie MLS.

## Vocali e temporanei

Registrazione e riproduzione usano file privati temporanei. Crash improvvisi possono
lasciare un file di playback fino all’avvio successivo; il controller elimina file con il
prefisso riservato all’inizializzazione. I blocchi incompleti restano cifrati nello stato
fino a sette giorni. La cancellazione logica non garantisce sovrascrittura flash.

## Supply chain

L’APK include codice e binari di Google/AndroidX, Briar Onion Wrapper e Tor. Lock e checksum
del wrapper Gradle riducono cambi involontari, non vulnerabilità upstream o compromissioni
del repository. Prima di una release pubblica servono advisory correnti, SBOM e audit.
