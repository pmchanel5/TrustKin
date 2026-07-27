# Brotherhood 0.2.1-alpha03-fix

Questa build corregge il blocco Android Keystore `Caller-provided IV not permitted` che impediva il salvataggio persistente di identità, contatti, messaggi e impostazioni.

## Correzioni

- Android Keystore genera ora autonomamente l'IV AES-GCM.
- Il formato dell'archivio locale resta compatibile con la lettura esistente.
- Il file cifrato viene scritto tramite `AtomicFile` per ridurre il rischio di archivi incompleti.
- Versione Android aggiornata a `0.2.1-alpha03-fix` (`versionCode 3`).

## Limiti ancora presenti

- Tor non è stato verificato su due telefoni reali.
- La LAN, i vocali e il funzionamento persistente in background richiedono ancora prove manuali.
- Questa è una build sperimentale non sottoposta ad audit di sicurezza indipendente.

## Test manuale consigliato

1. Installa l'APK debug.
2. Crea nome e PIN.
3. Chiudi completamente Brotherhood e riaprila.
4. Verifica che venga richiesto il PIN invece di ricreare l'identità.
5. Aggiungi un contatto, chiudi e riapri, quindi verifica che sia ancora presente.
