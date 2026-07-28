# Dipendenze

Sono ammessi soltanto Google Maven, Maven Central e Gradle Plugin Portal. Le versioni
dirette sono fissate in Gradle e le risoluzioni transitive in `app/gradle.lockfile`. Il
wrapper verifica lo SHA-256 della distribuzione Gradle 8.9.

| Dipendenza | Versione | Licenza dichiarata | Uso |
|---|---:|---|---|
| Android Gradle Plugin | 8.7.3 | Apache-2.0 | build Android |
| Kotlin | 2.0.21 | Apache-2.0 | linguaggio e plugin |
| Coroutines / Serialization | 1.9.0 / 1.7.3 | Apache-2.0 | concorrenza e JSON |
| AndroidX / Compose / Material 3 | versioni bloccate, BOM 2024.12.01 | Apache-2.0 | UI, lifecycle, WorkManager |
| WorkManager | 2.10.0 | Apache-2.0 | modalità Bilanciata |
| Google Tink Android | 1.16.0 | Apache-2.0 | ECIES, ECDSA, AES-GCM |
| ZXing | 3.5.3 / 4.3.0 | Apache-2.0 | QR locale |
| Onion Wrapper Android | 0.1.6 | GPLv3 | controllo Tor e onion service v3 |
| Do not kill me library | 0.2.7 transitiva | GPLv3 | wake lock richiesto dal wrapper |
| tor-android | 0.4.9.11 | BSD-3-Clause nei metadati Maven | `libtor.so` per quattro ABI |
| lyrebird-android | 0.6.2 | BSD-3-Clause nei metadati Maven | `liblyrebird.so` richiesto da Onion Wrapper per quattro ABI |
| JUnit / Kotlin test | 4.13.2 / 2.0.21 | EPL-1.0 / Apache-2.0 | test JVM |

## Valutazione Tor

La scelta è documentata in [TOR_INTEGRATION.md](TOR_INTEGRATION.md). Onion Wrapper è
pubblicato dal Briar Project, supporta `minSdk` inferiore a 28 e fornisce start/stop,
observer, SOCKS/control port e `ADD_ONION` v3. Il runtime 0.4.9.11 è lo stesso ramo
analizzato nel repository Briar al momento dell’iterazione.

I pacchetti `tor-android` e `lyrebird-android` contengono rispettivamente `libtor.so` e
`liblyrebird.so` per `armeabi-v7a`, `arm64-v8a`, `x86` e `x86_64`. Onion Wrapper prova ad
avviare entrambi: omettere Lyrebird impedisce l'avvio di Tor anche quando non sono
configurati bridge. La dimensione finale va rivalutata prima di una release stabile,
eventualmente con APK per ABI.

## Vulnerabilità e aggiornamenti

Nell’ambiente locale non era disponibile un database CVE affidabile aggiornato; non viene
quindi dichiarata l’assenza di vulnerabilità note. Dependabot resta attivo e la CI produce
un grafo bloccato. Prima della pubblicazione occorre confrontare Tor, Onion Wrapper, Tink,
AndroidX e toolchain con gli advisory correnti.

Non applicare automaticamente aggiornamenti di Tink o Tor: verificare compatibilità di
keyset, archivi, onion key, ABI e comportamento background. Ogni variazione del lock deve
essere revisionata.
