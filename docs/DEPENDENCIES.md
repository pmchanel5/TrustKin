# Dipendenze

Sono usati solo Google Maven, Maven Central e Gradle Plugin Portal. Le versioni dirette sono
fissate nei file Gradle e le risoluzioni transitive sono registrate in
`app/gradle.lockfile`; non esistono repository Maven sconosciuti. Il wrapper Gradle verifica
anche lo SHA-256 della distribuzione 8.9 prima di eseguirla.

| Dipendenza | Autore/licenza | Versione | Motivo | Stato/alternativa |
|---|---|---:|---|---|
| Android Gradle Plugin | Google / Apache-2.0 | 8.7.3 | build Android | mantenuto; nessuna alternativa equivalente |
| Kotlin + serialization + coroutines | JetBrains / Apache-2.0 | 2.0.21, 1.7.3, 1.9.0 | linguaggio, JSON, concorrenza | mantenuti; Java puro aumenterebbe complessità |
| AndroidX Core/Activity/Lifecycle/ExifInterface | Android Open Source Project / Apache-2.0 | fissate in Gradle | lifecycle, Compose host, lettura orientamento EXIF | mantenute |
| Jetpack Compose + Material 3 | Google / Apache-2.0 | BOM 2024.12.01 | UI Android | mantenuto; View XML è alternativa |
| Google Tink Android | Google / Apache-2.0 | 1.16.0 | primitive ECIES, ECDSA, AES-GCM | mantenuto; libsignal da rivalutare per sessioni |
| ZXing Core | ZXing / Apache-2.0 | 3.5.3 | generazione QR | mantenuto |
| ZXing Android Embedded | JourneyApps / Apache-2.0 | 4.3.0 | scansione QR locale | stabile; CameraX+ZXing riduce dipendenza futura |
| JUnit | JUnit / EPL-1.0 | 4.13.2 | test JVM | stabile |

Le dipendenze transitive devono essere riesaminate prima di ogni release. Dependabot è
configurato per segnalare aggiornamenti; gli aggiornamenti crittografici non devono essere
applicati senza test di compatibilità dei keyset esistenti.
