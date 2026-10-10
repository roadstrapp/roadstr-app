# Roadstr: routing e mappe offline. Fase 1 e progetto della fase 2

Lavori sul repository `roadstr` (branch di partenza `kotlin-native`). Il progetto è già stato analizzato e misurato: **non rifare la ricerca, parti dai fatti qui sotto.** Rispondi all'utente in italiano, diretto, senza preamboli. **Non dargli ragione per principio**: se una richiesta o una scelta qui sotto non regge sul codice, dillo con le prove.

## 0. Leggi prima di scrivere codice

1. `docs/offline-engine/RESEARCH_2026-10-08.md`: analisi, misure su desktop e su Pixel 10, decisioni. È la fonte per tutto ciò che segue.
2. `tools/routing_probe/`: app di prova usata per le misure (esempio funzionante di `valhalla-mobile` e di PMTiles in MapLibre).
3. `docs/world-discovery/DECISIONS.md` e `PLAN.md`: stile delle decisioni del progetto. Aggiungi le tue decisioni nello stesso formato in `docs/offline-engine/DECISIONS.md`.
4. Il codice che toccherai: `android/app/src/main/kotlin/app/roadstr/service/routing/NativeRoutingService.kt`, `core/network/Routing*Protocol.kt` (in particolare `RoutingResponseStep`, `RoutingParsedRoute`), `feature/route/`, `feature/history/NativeRouteHistory*.kt`, `feature/saved/`, `storage/*`. **Verifica tu quale modulo Gradle produce l'APK di release** (`android/app` oppure `native-android`, che condivide i sorgenti) e dove vanno i test.

## 1. Decisioni già prese (non riaprirle senza prove)

- **Motore di routing offline: Valhalla tramite `io.github.rallista:valhalla-mobile:0.6.4`** (MIT, wrapper JNI, arm64, allineato a 16 KB, solo libc/liblog/libz/libm/libdl). BRouter è scartato: sul Pixel 10 impiega 2–37 s per percorso e fallisce 4 percorsi su 16. Organic Maps/CoMaps è scartato.
- **Il routing online resta com'è** (OSRM, Valhalla, GraphHopper, ORS). Il motore locale sta dietro un'interfaccia comune.
- **Tutto ciò che è offline è opt-in, deciso dal proprietario:** il routing offline e il download dei pacchetti (mappe e dati di routing) per la navigazione totalmente offline sono **funzioni che l'utente sceglie di attivare**, mai attive di default. Conseguenze obbligatorie:
  - Impostazione "routing offline" **spenta** di default. Senza attivarla l'app si comporta esattamente come oggi.
  - **Nessun download automatico, nessun download in background non richiesto, nessun precaricamento.** Ogni download parte da una scelta esplicita dell'utente, dopo aver mostrato la dimensione e lo spazio libero, con possibilità di annullare e di cancellare i pacchetti.
  - **Nessuna richiesta di rete verso l'host dei pacchetti finché l'utente non apre la schermata dei pacchetti o avvia un download.** Niente controlli di aggiornamento all'avvio. Chi non attiva la funzione non contatta mai quell'host.
  - Il motore locale, anche quando è attivo, si usa solo se la copertura installata contiene il percorso; altrimenti l'app usa il provider online scelto come adesso, o dice chiaramente che non c'è copertura. Mai un ripiego silenzioso che cambi il comportamento o la privacy senza che l'utente lo sappia.
  - La scelta di **come** scaricare (solo Wi-Fi, avviso sui dati mobili) è un dettaglio da proporre nel progetto, con il valore di default più prudente.
- **Mappe offline: PMTiles** (MapLibre Android 13.5.2 supporta `pmtiles://file://`), generati con Planetiler (Apache-2.0, profilo OpenMapTiles). Niente `OfflineManager` per interi paesi.
- **Ricerca offline: SQLite con FTS5** tramite `androidx.sqlite:sqlite-bundled` (verificato: FTS5 incluso). Fase successiva.
- **Niente Overture Maps**, niente LLM locale per il "cervello" di ricerca (evoluzione dei lessici deterministici in `core/discovery`).

## 2. Fatti misurati su cui puoi contare

- Dati Valhalla per l'Italia, solo auto (`mjolnir.include_bicycle=false`, `include_pedestrian=false`): **1,76 GB** (`tiles.tar`), 623 MB con zstd -19. Livello 0 (autostrade) 81 MB, livello 1 294 MB, livello 2 (strade locali) **1.384 MB**.
- **Pacchetto corridoio** (verificato): livelli 0 e 1 d'Italia (375 MB) + tile di livello 2 entro un margine dal percorso (id tile = `riga * 1440 + colonna`, tile da 0,25°), riuniti con `valhalla_build_extract`. Firenze–Perugia: 423 MB (margine ±0,1°), 455 MB (±0,25°). Stesso percorso del pacchetto completo, ricalcoli riusciti, fuori dal corridoio errore `No suitable edges near location` (codice 171): **va mostrato come "area non scaricata", mai come una linea retta**.
- Sul Pixel 10 (top di gamma, quindi tempi ottimistici): motore creato in 0,75 s, percorso 40–148 ms, 580 km in 148 ms, memoria 166–420 MB. Va rimisurato su un telefono di fascia media.
- Build dei dati: Valhalla Italia ≈ 7–9 min, **serve più di 3,5 GB di RAM** (con 9 GB riesce). Planetiler Italia fino a z14: 1,68 GB (235 MB fino a z12, 572 MB fino a z13), circa 9 min con 2,5 GB di heap.
- API di `valhalla-mobile` da usare: `ValhallaConfigBuilder().withTileExtract(path).build()`, `Valhalla(context, config)`, `routeRaw(json)` (restituisce il JSON di Valhalla: **riusa `RoutingResponseProtocol` invece dei modelli generati**). Servono come dipendenze esplicite anche `io.github.rallista:valhalla-models-config:0.6.0` e `valhalla-models:0.6.0`. Un'istanza è pesante e tiene aperto il tar mappato in memoria: **una sola istanza per set di dati, riusata, e `close()` alla fine**. Il gestore di default scrive sempre `valhalla.json` nello stesso file: usa un file di configurazione dedicato per istanza. Il modulo della libreria compila con Java 21: controlla la compatibilità col nostro target.
- I miei dati Valhalla sono stati costruiti **senza** elevazione, fusi orari e configurazione delle velocità di default. Per la produzione serve decidere e documentare la configurazione.

## 3. Regole del progetto (non negoziabili)

- **Mai pushare su `main`**. Lavora su un branch nuovo da `kotlin-native` (es. `feature/offline-routing`). Non fare push senza che l'utente lo chieda. Non usare `git commit --amend` né riscrivere la storia.
- **Mai adb.** L'utente gestisce i suoi telefoni. Quello che richiede un dispositivo lo scrivi come istruzioni di test per lui (basate su `tools/routing_probe/`).
- **Privacy, repo pubblico:** nessuna identità né città dell'utente in codice, commenti, test, documenti. Niente nomi di città dell'Emilia-Romagna nei test. Usa città pubbliche a caso lontane da lì.
- **Nessuna traccia di AI** nei commit, nei changelog, nei commenti, nelle note di rilascio. Messaggi di commit senza trailer; l'identità è quella già configurata in git.
- **Niente Google:** nessuna dipendenza `com.google.android.gms`, `play`, `firebase`, `mlkit` (c'è già un'esclusione Gradle e un test anti-regressione: non toccarli).
- **Licenze:** il repo è MIT. Aggiorna `THIRD_PARTY_NOTICES.md` per ogni nuova dipendenza e per i dati (OSM sotto ODbL, OpenMapTiles).
- **Le funzioni esistenti non si rompono:** segnalazioni Nostr, GPS, voce, POI, preferiti, ZTL, privacy, 27 lingue, routing online. Niente refactoring invasivo non giustificato.
- **Test di contratto Dart:** molti `test/native_*_contract_test.dart` leggono i sorgenti Kotlin come testo. Rinominare in Kotlin può romperli. **Non eseguire mai `dart format` su tutto il repo.**
- **Regola dell'indentazione:** massimo 3 livelli. Se servono di più, spezza la funzione o inverti la condizione (early return, funzione estratta).
- Le stringhe dell'interfaccia vanno nelle 27 lingue seguendo i generatori esistenti (`tools/kotlin_rewrite/generate_android_*_strings.dart`). Non lasciare chiavi senza traduzione.
- **Non dichiarare verificato ciò che non hai eseguito.** Distingui sempre: eseguito e passato / scritto ma non eseguito / ipotesi.

## 4. Roadmap (una fase alla volta, fermati alla fine di ognuna)

| Fase | Contenuto | Dipende da |
|---|---|---|
| **1** | Itinerari salvati (Obiettivo 4) | nulla |
| **2** | Contratto del motore locale + adattatore Valhalla (dietro impostazione) | – |
| **3** | Pacchetti di routing: manifest, download riprendibile, verifica, installazione atomica, copertura, strumenti di costruzione | 2 |
| **4** | Mappe PMTiles scaricabili su scelta dell'utente, stesso gestore di download | 3 |
| **5** | Ricalcolo offline in navigazione | 2, 3 |
| **6** | Ricerca offline (SQLite FTS5) | 3 |
| **7** | Evoluzione del "cervello" di ricerca | indipendente |

**Ora fai solo la Fase 1 e scrivi il progetto (solo documento, nessun codice) delle Fasi 2 e 3. Poi fermati e riferisci.**

### Fase 1: itinerari salvati

Obiettivo: pianificare un itinerario con tappe intermedie, dargli un nome, salvarlo in modo permanente, ritrovarlo in una sezione dedicata, mostrarlo sulla mappa, modificarne tappe e preferenze, ricalcolarlo, avviare la navigazione anche dopo giorni.

- **Modello dati** (`SavedRoute`): id stabile, nome, data di creazione e di modifica, tappe ordinate (coordinate + etichetta), preferenze (profilo, evitamenti come in `RoutingRouteAvoidance`), provider usato, data del calcolo, distanza, durata, **geometria** (polyline a precisione 6), **manovre** (i campi di `RoutingResponseStep`, comprese `roadName`, `roadRef`, `exitNumber`), eventuali limiti di velocità (`RoutingSpeedLimitEntry`). Riserva un campo per il motore usato e uno per "ricalcolabile offline" (oggi sempre falso: lo userà la Fase 3).
- **Due stati distinti**: itinerario con geometria e istruzioni già calcolate (navigabile senza rete, ma una deviazione non si ricalcola senza motore locale) e itinerario "da ricalcolare". Se la rete manca e non c'è motore locale, la navigazione parte dalla geometria salvata e **la deviazione deve dichiararlo all'utente**, non fingere un ricalcolo.
- **Persistenza:** segui il modello di `NativeSavedPlacesStore` e di `NativeRouteHistory` (cifratura con l'alias dedicato, come la cronologia dei percorsi). Limite di numero e di dimensione ragionevoli e documentati. Migrazione e versione dello schema da subito.
- **Interfaccia:** una sezione dedicata raggiungibile da dove si trova la cronologia dei percorsi; azioni: salva dal percorso corrente, rinomina, modifica tappe, ricalcola, elimina, avvia. Segui i pattern esistenti (`SheetDragToDismiss`, pannelli in `feature/`), nessun framework nuovo.
- **Non fare:** esportazione GPX/GeoJSON, sincronizzazione Nostr (rimandate), motore offline.
- **Opt-in:** la Fase 1 non introduce alcuna dipendenza da funzioni offline; un itinerario salvato resta utilizzabile come descritto sopra anche con il routing offline spento.
- **Criteri di accettazione:** test JVM sul modello e sullo store (serializzazione, migrazione, limiti, dati corrotti, ordine delle tappe), test del flusso "salva → riapri → avvia" con motore di routing finto, nessuna regressione nei test esistenti (esegui l'intera suite Kotlin e Dart rilevante e riporta il risultato), stringhe in 27 lingue.

### Progetto delle Fasi 2 e 3 (solo documento, `docs/offline-engine/DESIGN_PHASE_2_3.md`)

Proponi, adattandoti ai pattern già nel codice, senza framework nuovi:

1. **Interfaccia del motore di routing** su cui stanno insieme i provider online (`NativeRoutingService`) e il motore locale; come il motore locale restituisce `RoutingParsedRoute` riusando `RoutingResponseProtocol`; come si mappano gli errori (171 → "area non scaricata"); come si sceglie il motore (impostazione **opt-in**, copertura, rete) rispettando le regole opt-in della sezione 1. Schermata delle impostazioni e testi che spiegano cosa si scarica, quanto pesa e cosa succede senza copertura.
2. **Gestione dei pacchetti (tutta opt-in):** formato del manifest (id, versione, tipo di dataset, area, livelli, dimensione, sha256, URL, licenza/attribuzione), download riprendibile con `Range`, file `.part`, verifica sha256, installazione atomica per rinomina, aggiornamento, cancellazione, stima dello spazio prima del download, gestione di aree sovrapposte e confinanti, query di copertura ("questo punto/percorso è coperto?").
3. **Strumenti di costruzione dei pacchetti** (script in `tools/`, riproducibili): PBF → Planetiler (PMTiles) e → Valhalla (scheletro livelli 0–1, regioni, corridoi), con la memoria necessaria dichiarata. Dove ospitare i file statici è **una decisione dell'utente**: scrivila come domanda aperta, non assumerla.
4. Cosa misurare sul telefono dell'utente prima di chiudere ogni fase (fascia media, batteria in uso, avvio a freddo della mappa con lo stile vero), con i passi precisi.
5. Rischi e domande aperte, in particolare: compatibilità Java 21 del wrapper, manutentore unico di `valhalla-mobile` (piano B: compilare il wrapper da noi con NDK 29 e vcpkg), configurazione di produzione di Valhalla (velocità di default, fusi orari, elevazione), dimensione dello scheletro nazionale (375 MB).

## 5. Consegna

- Commit piccoli e coerenti sul branch di lavoro; nessun push.
- Alla fine: elenco di cosa è stato eseguito (con i comandi e l'esito), cosa è solo scritto, cosa resta da provare sul telefono dell'utente, decisioni prese (con il motivo) e domande aperte.
- Il codice sarà rivisto da un altro agente: scrivi test che dimostrino il comportamento, non solo che il codice gira.
