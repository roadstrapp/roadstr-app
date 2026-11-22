(() => {
  'use strict';

  /* ------------------------------------------------------------------------
     1. Bilingual Copy (EN / IT)
     ------------------------------------------------------------------------ */
  const TRANSLATIONS = {
    en: {
      'nav.features': 'Features',
      'nav.events': 'Road Events',
      'nav.screenshots': 'Screens',
      'nav.privacy': 'Privacy',
      'nav.protocols': 'Nostr',
      'nav.download': 'Download',
      'nav.getApp': 'Get APK',
      'hero.badge': 'v1.4.0 · Native Kotlin · MIT Open Source · Android',
      'hero.titleLead': 'Navigate freely.',
      'hero.titleAccent': 'Powered by Nostr & OpenStreetMap.',
      'hero.subtitle': 'Open-source, decentralised turn-by-turn GPS navigation for Android. Real-time community road alerts over Nostr, on-device AI voice guidance, downloadable offline maps, worldwide public transport, and Lightning zaps — with zero accounts, zero tracking, and zero Google Play Services.',
      'hero.downloadCta': 'Download v1.4.0 APK',
      'hero.githubCta': 'View Source',
      'hero.metricLang': 'Languages',
      'hero.metricOffline': 'Offline Cities',
      'hero.metricTrackers': 'Trackers & Accounts',
      'hero.metricLicense': 'Open Source',
      'hero.chip1': 'Speed camera ahead',
      'hero.chip2': 'Zap reporters via NWC',
      'hero.chip3': '100% On-Device AI Voice',
      'themeStrip.desc': 'Roadstr ships with four built-in palettes — Nostr Violet & Bitcoin Orange in Light and Dark — plus solar auto dark mode via NOAA sunrise/sunset calculation. Try them live:',
      'themeStrip.darkNostr': 'Dark · Violet',
      'themeStrip.darkBitcoin': 'Dark · Bitcoin',
      'themeStrip.lightNostr': 'Light · Violet',
      'themeStrip.lightBitcoin': 'Light · Bitcoin',
      'features.kicker': 'ENGINEERED FOR SOVEREIGN NAVIGATION',
      'features.title': 'Everything you expect from a modern navigator. None of the surveillance.',
      'features.f1Title': 'Native 3D Navigation & Offline Maps',
      'features.f1Desc': 'Built in native Kotlin with Jetpack Compose and MapLibre Native. Enjoy 3D tilt, speed-adaptive zoom, foreground screen-off guidance, motorway U-turn prevention on reroute, live OSM speed limits, and ZTL / limited-traffic warnings. Download verified Geofabrik Shortbread MBTiles and route offline with the on-device Valhalla engine across 34,000+ indexed cities.',
      'features.f2Title': 'Decentralised Road Alerts',
      'features.f2Desc': 'Publish and verify hazards, speed cameras, traffic jams, and road closures as signed Nostr events. Shared format with Roadbeacon, vote-weighted duration, owner-signed corrections (kind 1317), and experimental MeshCore LoRa radio support for off-grid alerts.',
      'features.f3Title': '100% On-Device AI Voice',
      'features.f3Desc': 'Natural neural voice guidance powered locally by Kokoro-82M (7 languages) and Piper Thorsten-Voice (German) via ONNX Runtime + eSpeak NG. Plus experimental offline voice search with NVIDIA Parakeet TDT 0.6B v3 across 25 languages — no audio ever leaves your phone.',
      'features.f4Title': 'Bitcoin Lightning Tips',
      'features.f4Desc': 'Reward drivers who report hazards on time with instant Bitcoin Lightning zaps. Connect Alby Hub, Mutiny, or Cashu NWC via Nostr Wallet Connect (preferring NIP-44 v2 encryption), or pay via any installed Lightning wallet.',
      'features.f5Title': 'Worldwide Public Transit',
      'features.f5Desc': 'Multimodal public-transport routing powered by open Transitous feeds worldwide and Entur in Norway. Browse live departure boards, compare saved journeys, and keep offline snapshots of itineraries with zero proprietary API keys.',
      'features.f6Title': 'Explore, Smart Search & Opt-In Journey',
      'features.f6Desc': 'Search naturally for "pharmacy open now" or "EV chargers along the route" using a deterministic local parser over OpenStreetMap — plus optional self-chosen SearXNG web results and an in-app Wikipedia reader. Browse categories in Explore, pick from 5 Speedometer HUD styles, and optionally track validated distance milestones with 17 Journey achievements stored strictly on-device.',
      'events.kicker': 'COMMUNITY INTELLIGENCE · NOSTR KIND 1315',
      'events.title': 'Illustrated road alerts shared across Nostr & Roadbeacon',
      'events.lead': 'Click any road-event category to inspect its signed Nostr kind-1315 event structure. Every report is cryptographically verified before appearing on your map.',
      'events.sigVerified': 'BIP-340 Schnorr signature verified locally',
      'events.specLink': 'Read shared spec ↗',
      'cursors.kicker': 'CUSTOM VEHICLE CURSORS',
      'cursors.title': 'Make the map yours — from Classic 500 to the Nostr Ostrich',
      'screens.kicker': 'REAL DEVICE SCREENSHOTS · ROME',
      'screens.title': "Clean glass chrome that stays out of the road's way",
      'screens.tabNav': 'Guidance',
      'screens.tabMap': 'Map',
      'screens.tabRoute': 'Route',
      'screens.tabPlace': 'Place',
      'screens.s1Title': 'Map & Quick Navigate',
      'screens.s1Desc': 'MapLibre Native vector map with custom cursor and floating glass controls.',
      'screens.s2Title': 'Multi-Modal Route Planner',
      'screens.s2Desc': 'Car, bike, walking, and transit alternatives with live Open-Meteo route weather.',
      'screens.s3Title': 'Turn-by-Turn HUD',
      'screens.s3Desc': 'Crisp manoeuvre tiles, live speed-limit sign, custom speedometer, and one-tap event reporting.',
      'screens.s4Title': 'Rich OSM Place Cards',
      'screens.s4Desc': 'Contextual OSM details, EV/Bitcoin/parking metadata, and restricted in-app Wikipedia reader.',
      'privacy.kicker': 'PRIVACY BY ARCHITECTURE · RADICAL HONESTY',
      'privacy.title': 'Your keys never touch Roadstr. Your trips never fund an ad network.',
      'privacy.p1Title': 'Zero Private Keys in the App',
      'privacy.p1Desc': 'Roadstr intentionally removed pasted nsec login. Authenticate exclusively through Amber (NIP-55) or a remote Bunker (NIP-46) so your signing key stays isolated outside the app.',
      'privacy.p2Title': 'NIP-44 End-to-End Sync',
      'privacy.p2Desc': 'Saved places and your parking spot stay local-only by default. If you enable cross-device sync or Journey shards over Nostr, payloads are encrypted with NIP-44 v2 to your own key — relays see only ciphertext.',
      'privacy.p3Title': 'Zero Google Play Services',
      'privacy.p3Desc': "Reads GPS directly from Android's native LocationManager. Runs seamlessly on GrapheneOS, CalyxOS, LineageOS, and any degoogled Android device with zero analytics or telemetry SDKs.",
      'privacy.p4Title': 'Honest Threat Model',
      'privacy.p4Desc': 'Roadstr runs no first-party servers. Online routing, Overpass lookups, Transitous, and weather send anonymous requests without persistent IDs. Use offline maps or a VPN if you want to hide IP-linked coordinates — and remember public Nostr/LoRa reports are pseudonymous, not anonymous.',
      'protocols.kicker': 'OPEN STANDARDS & NOSTR STACK',
      'protocols.title': 'Built on open protocols anyone can inspect or self-host',
      'protocols.n01': 'Base Nostr relay protocol',
      'protocols.n19': 'Bech32 identity encoding',
      'protocols.n40': 'Relay event expiration',
      'protocols.n44': 'Versioned v2 encryption',
      'protocols.n46': 'Remote signer (bunker://)',
      'protocols.n47': 'Nostr Wallet Connect',
      'protocols.n55': 'Amber Android Signer',
      'protocols.n57': 'Lightning Zap receipts',
      'protocols.n78': 'Encrypted app snapshots',
      'protocols.k1315': 'Road reports, votes & edits',
      'protocols.rb': 'Shared road-event format',
      'protocols.mc': 'Experimental LoRa radio',
      'download.title': 'Get Roadstr on your Android device',
      'download.subtitle': 'Signed reproducible APKs straight from GitHub Releases, plus Zapstore & F-Droid metadata. Upgrades cleanly over 0.5.x without losing your data.',
      'download.recommended': 'Recommended',
      'download.universal': 'Universal',
      'download.arm64Desc': 'Modern 64-bit Android phones (Pixel, GrapheneOS, Samsung, etc.)',
      'download.uniDesc': 'Contains all ABIs — works on any supported Android architecture.',
      'download.otherDesc': '32-bit ARM, x86_64 emulators, release notes, and full SHA256SUMS.',
      'download.downloadNow': 'Download APK →',
      'download.openReleases': 'GitHub Releases ↗',
      'download.copy': 'Copy',
      'download.buildSummary': 'Or build from source yourself',
      'footer.tagline': 'Open-source decentralised navigation · MIT License',
      'footer.releases': 'Releases',
      'footer.privacy': 'Privacy',
      'footer.attrib': 'Map data © OpenStreetMap contributors · Built with Nostr, MapLibre & Bitcoin Lightning.',
      'toast.copied': 'Copied to clipboard!',
    },
    it: {
      'nav.features': 'Funzionalità',
      'nav.events': 'Segnalazioni',
      'nav.screenshots': 'Schermate',
      'nav.privacy': 'Privacy',
      'nav.protocols': 'Nostr',
      'nav.download': 'Scarica',
      'nav.getApp': 'Scarica APK',
      'hero.badge': 'v1.4.0 · Kotlin Nativo · MIT Open Source · Android',
      'hero.titleLead': 'Naviga libero.',
      'hero.titleAccent': 'Su Nostr e OpenStreetMap.',
      'hero.subtitle': 'Navigazione GPS turn-by-turn open source e decentralizzata per Android. Segnalazioni stradali in tempo reale su Nostr, voce AI locale sul dispositivo, mappe offline scaricabili, trasporto pubblico mondiale e zap Lightning — senza account, senza tracciamento e senza Google Play Services.',
      'hero.downloadCta': 'Scarica APK v1.4.0',
      'hero.githubCta': 'Codice Sorgente',
      'hero.metricLang': 'Lingue',
      'hero.metricOffline': 'Città Offline',
      'hero.metricTrackers': 'Tracker e Account',
      'hero.metricLicense': 'Open Source',
      'hero.chip1': 'Autovelox segnalato',
      'hero.chip2': 'Zap ai reporter via NWC',
      'hero.chip3': 'Voce AI 100% On-Device',
      'themeStrip.desc': 'Roadstr integra quattro temi ufficiali — Nostr Violet e Bitcoin Orange in versione Chiara e Scura — più cambio automatico alba/tramonto con algoritmo solare NOAA. Provali dal vivo:',
      'themeStrip.darkNostr': 'Scuro · Violet',
      'themeStrip.darkBitcoin': 'Scuro · Bitcoin',
      'themeStrip.lightNostr': 'Chiaro · Violet',
      'themeStrip.lightBitcoin': 'Chiaro · Bitcoin',
      'features.kicker': 'PROGETTATA PER UNA NAVIGAZIONE SOVRANA',
      'features.title': 'Tutto ciò che serve in un navigatore moderno. Zero sorveglianza.',
      'features.f1Title': 'Navigazione 3D Nativa e Mappe Offline',
      'features.f1Desc': 'Scritta in Kotlin nativo con Jetpack Compose e MapLibre Native. Inclinazione 3D, zoom adattivo alla velocità, guida attiva a schermo spento, blocco delle inversioni a U in autostrada, limiti di velocità OSM e avvisi ZTL. Scarica mappe verificate Geofabrik Shortbread MBTiles e calcola percorsi offline con Valhalla su oltre 34.000 città.',
      'features.f2Title': 'Segnalazioni Stradali Decentralizzate',
      'features.f2Desc': 'Pubblica e conferma autovelox, pattuglie, traffico, incidenti e chiusure come eventi Nostr firmati. Formato condiviso con Roadbeacon, durata guidata dai voti, correzioni firmate dall’autore (kind 1317) e supporto sperimentale radio MeshCore LoRa.',
      'features.f3Title': 'Voce AI 100% Sul Dispositivo',
      'features.f3Desc': 'Guida vocale neurale in locale grazie a Kokoro-82M (7 lingue, incluso l’italiano) e Piper Thorsten-Voice (tedesco) con ONNX Runtime + eSpeak NG. In più ricerca vocale offline sperimentale con NVIDIA Parakeet TDT 0.6B v3 in 25 lingue: nessun audio lascia il telefono.',
      'features.f4Title': 'Mance Bitcoin Lightning',
      'features.f4Desc': 'Premia chi segnala pericoli stradali utili con zap Bitcoin Lightning istantanei. Collega Alby Hub, Mutiny o Cashu NWC via Nostr Wallet Connect (con cifratura NIP-44 v2) oppure paga con qualsiasi wallet Lightning installato.',
      'features.f5Title': 'Trasporto Pubblico Mondiale',
      'features.f5Desc': 'Percorsi multimodali con i feed aperti di Transitous in tutto il mondo ed Entur in Norvegia. Consulta tabelloni partenze live, confronta tragitti salvati e conserva snapshot leggibili offline senza chiavi API proprietarie.',
      'features.f6Title': 'Esplora, Ricerca Naturale e Viaggio',
      'features.f6Desc': 'Cerca in linguaggio naturale come "farmacia aperta ora" o "colonnine lungo il percorso" tramite parser locale su OpenStreetMap, con risultati web opzionali dal tuo SearXNG e lettore Wikipedia integrato. Scegli tra 5 tachimetri HUD e sblocca 17 obiettivi di Viaggio salvati sul telefono.',
      'events.kicker': 'INTELLIGENZA COMUNITARIA · NOSTR KIND 1315',
      'events.title': 'Segnalazioni illustrate condivise tra Nostr e Roadbeacon',
      'events.lead': 'Clicca su una categoria per ispezionare l’evento Nostr kind 1315 firmato. Ogni segnalazione viene verificata crittograficamente prima di apparire sulla mappa.',
      'events.sigVerified': 'Firma Schnorr BIP-340 verificata in locale',
      'events.specLink': 'Leggi la specifica condivisa ↗',
      'cursors.kicker': 'CURSORI VEICOLO PERSONALIZZATI',
      'cursors.title': 'Scegli il tuo stile — dalla 500 Classica allo Struzzo Nostr',
      'screens.kicker': 'SCREENSHOT REALI · ROMA',
      'screens.title': 'Interfaccia glass pulita che lascia spazio alla strada',
      'screens.tabNav': 'Guida',
      'screens.tabMap': 'Mappa',
      'screens.tabRoute': 'Percorso',
      'screens.tabPlace': 'Luogo',
      'screens.s1Title': 'Mappa e Avvio Rapido',
      'screens.s1Desc': 'Mappa vettoriale MapLibre Native con cursore personalizzato e controlli glass flottanti.',
      'screens.s2Title': 'Pianificazione Multimodale',
      'screens.s2Desc': 'Auto, bici, a piedi e mezzi pubblici con meteo Open-Meteo lungo il percorso.',
      'screens.s3Title': 'HUD Turn-by-Turn',
      'screens.s3Desc': 'Manovre chiare, segnale limite di velocità live, tachimetro personalizzabile e segnalazione rapida.',
      'screens.s4Title': 'Schede Luoghi OSM',
      'screens.s4Desc': 'Dettagli OSM contestuali (Bitcoin/Lightning, parcheggi, ricarica EV) e Wikipedia integrata.',
      'privacy.kicker': 'PRIVACY BY DESIGN · TRASPARENZA TOTALE',
      'privacy.title': 'Le tue chiavi non toccano mai Roadstr. I tuoi viaggi non finanziano pubblicità.',
      'privacy.p1Title': 'Nessuna Chiave Privata nell’App',
      'privacy.p1Desc': 'Il login con nsec incollato è stato rimosso intenzionalmente. Accedi solo tramite Amber (NIP-55) o un Bunker remoto (NIP-46) così la tua chiave resta isolata fuori dall’app.',
      'privacy.p2Title': 'Sync End-to-End con NIP-44',
      'privacy.p2Desc': 'Preferiti e posizione del parcheggio restano locali di default. Se attivi la sincronizzazione o i frammenti Viaggio su Nostr, i dati sono cifrati con NIP-44 v2 verso la tua chiave.',
      'privacy.p3Title': 'Zero Google Play Services',
      'privacy.p3Desc': 'Legge il GPS direttamente dal LocationManager nativo di Android. Funziona perfettamente su GrapheneOS, CalyxOS, LineageOS e dispositivi degoogled senza SDK di telemetria.',
      'privacy.p4Title': 'Modello di Minaccia Onesto',
      'privacy.p4Desc': 'Roadstr non gestisce server centrali. Routing online, Overpass, Transitous e meteo ricevono richieste anonime senza ID persistenti. Usa mappe offline o una VPN se vuoi nascondere l’IP: ricorda che le segnalazioni Nostr/LoRa pubbliche sono pseudonime, non anonime.',
      'protocols.kicker': 'STANDARD APERTI E STACK NOSTR',
      'protocols.title': 'Basata su protocolli aperti ispezionabili e self-hostabili',
      'protocols.n01': 'Protocollo base relay Nostr',
      'protocols.n19': 'Codifica identità Bech32',
      'protocols.n40': 'Scadenza eventi sui relay',
      'protocols.n44': 'Cifratura versionata v2',
      'protocols.n46': 'Signer remoto (bunker://)',
      'protocols.n47': 'Nostr Wallet Connect',
      'protocols.n55': 'Amber Android Signer',
      'protocols.n57': 'Ricevute Lightning Zap',
      'protocols.n78': 'Snapshot app cifrati',
      'protocols.k1315': 'Segnalazioni, voti e correzioni',
      'protocols.rb': 'Formato condiviso Roadbeacon',
      'protocols.mc': 'Radio LoRa sperimentale',
      'download.title': 'Installa Roadstr sul tuo dispositivo Android',
      'download.subtitle': 'APK firmati e verificabili da GitHub Releases, più metadati Zapstore e F-Droid. Si aggiorna sopra la 0.5.x senza perdere i tuoi dati.',
      'download.recommended': 'Consigliato',
      'download.universal': 'Universale',
      'download.arm64Desc': 'Smartphone Android 64-bit moderni (Pixel, GrapheneOS, Samsung, ecc.)',
      'download.uniDesc': 'Include tutte le architetture — funziona su qualsiasi Android supportato.',
      'download.otherDesc': 'ARM 32-bit, emulatori x86_64, note di rilascio e file SHA256SUMS completo.',
      'download.downloadNow': 'Scarica APK →',
      'download.openReleases': 'GitHub Releases ↗',
      'download.copy': 'Copia',
      'download.buildSummary': 'Oppure compila dal codice sorgente',
      'footer.tagline': 'Navigazione decentralizzata open source · Licenza MIT',
      'footer.releases': 'Rilasci',
      'footer.privacy': 'Privacy',
      'footer.attrib': 'Dati mappa © collaboratori OpenStreetMap · Realizzata con Nostr, MapLibre e Bitcoin Lightning.',
      'toast.copied': 'Copiato negli appunti!',
    }
  };

  /* ------------------------------------------------------------------------
     2. Road Events Interactive Data (accurate to Roadstr 1.4.0 & Roadbeacon)
     ------------------------------------------------------------------------ */
  const ROAD_EVENTS = [
    {
      id: 'speed_camera',
      icon: 'assets/road_events/speed_camera.png',
      en: 'Speed Camera',
      it: 'Autovelox',
      transportEn: 'Relays + MeshCore LoRa',
      transportIt: 'Relay + MeshCore LoRa',
      descEn: 'Community-reported fixed or mobile speed camera. Confirmations (kind 1316) extend visibility; dismissals retire stale markers early.',
      descIt: 'Autovelox fisso o mobile segnalato dalla community. Le conferme (kind 1316) ne estendono la durata; le smentite lo rimuovono prima.',
      lat: '41.890210',
      lon: '12.492231',
      ttlMin: 120,
      note: 'Mobile speed check near Colosseum ring road',
    },
    {
      id: 'police',
      icon: 'assets/road_events/police.png',
      en: 'Police',
      it: 'Polizia',
      transportEn: 'Relays + MeshCore LoRa',
      transportIt: 'Relay + MeshCore LoRa',
      descEn: 'Roadside checkpoint or patrol reported in real time, signed with your Amber or bunker key and verified by nearby drivers.',
      descIt: 'Posto di blocco o pattuglia segnalata in tempo reale, firmata via Amber o bunker e verificata dagli altri conducenti.',
      lat: '41.886140',
      lon: '12.485912',
      ttlMin: 60,
      note: 'Checkpoint on Viale Aventino southbound',
    },
    {
      id: 'traffic_jam',
      icon: 'assets/road_events/traffic_jam.png',
      en: 'Traffic Jam',
      it: 'Traffico',
      transportEn: 'Relays + MeshCore LoRa',
      transportIt: 'Relay + MeshCore LoRa',
      descEn: 'Heavy congestion alert published directly to public Nostr relays — no central telemetry server required.',
      descIt: 'Segnalazione di coda o traffico intenso pubblicata direttamente sui relay Nostr pubblici, senza server centralizzati.',
      lat: '41.898611',
      lon: '12.476873',
      ttlMin: 45,
      note: 'Standstill traffic approaching Lungotevere',
    },
    {
      id: 'accident',
      icon: 'assets/road_events/accident.png',
      en: 'Accident',
      it: 'Incidente',
      transportEn: 'Relays + MeshCore LoRa',
      transportIt: 'Relay + MeshCore LoRa',
      descEn: 'High-priority collision warning with exact 6-decimal coordinates shared seamlessly between Roadstr and Roadbeacon.',
      descIt: 'Avviso di incidente ad alta priorità con coordinate a 6 decimali condiviso in modo nativo tra Roadstr e Roadbeacon.',
      lat: '41.902180',
      lon: '12.457320',
      ttlMin: 90,
      note: 'Right lane blocked after tunnel exit',
    },
    {
      id: 'construction',
      icon: 'assets/road_events/construction.png',
      en: 'Construction',
      it: 'Lavori in corso',
      transportEn: 'Relays + MeshCore LoRa',
      transportIt: 'Relay + MeshCore LoRa',
      descEn: 'Roadworks and lane narrowings. Authors can issue authoritative kind-1317 updates or approve community kind-1318 edits.',
      descIt: 'Cantieri e restringimenti di carreggiata. Gli autori possono pubblicare aggiornamenti kind 1317 o approvare correzioni kind 1318.',
      lat: '41.894450',
      lon: '12.482104',
      ttlMin: 240,
      note: 'Single lane roadworks until evening',
    },
    {
      id: 'road_closure',
      icon: 'assets/road_events/road_closure.png',
      en: 'Road Closure',
      it: 'Strada chiusa',
      transportEn: 'Relays + MeshCore LoRa',
      transportIt: 'Relay + MeshCore LoRa',
      descEn: 'Blocked street or temporary closure so drivers can reroute cleanly before getting trapped in traffic.',
      descIt: 'Strada chiusa o temporaneamente interdetta, per ricalcolare il percorso prima di restare bloccati.',
      lat: '41.900912',
      lon: '12.483318',
      ttlMin: 180,
      note: 'Street closed for local event',
    },
    {
      id: 'surveillance_camera',
      icon: 'assets/road_events/surveillance_camera.png',
      en: 'Surveillance',
      it: 'Telecamera',
      transportEn: 'Nostr Relays Only',
      transportIt: 'Solo Relay Nostr',
      descEn: 'Added in v1.2.1 to map public surveillance cameras. Published to Nostr relays only — intentionally never transmitted over MeshCore LoRa radio.',
      descIt: 'Introdotta nella v1.2.1 per mappare telecamere di sorveglianza. Pubblicata solo sui relay Nostr, mai trasmessa via radio MeshCore LoRa.',
      lat: '41.891930',
      lon: '12.490115',
      ttlMin: 720,
      note: 'ANPR surveillance camera at intersection',
    },
    {
      id: 'hazard',
      icon: 'assets/road_events/hazard.png',
      en: 'Hazard',
      it: 'Pericolo',
      transportEn: 'Relays + MeshCore LoRa',
      transportIt: 'Relay + MeshCore LoRa',
      descEn: 'Debris, fallen branches, flooded underpasses, or sudden obstacles on the carriageway.',
      descIt: 'Detriti, rami caduti, sottopassi allagati o ostacoli improvvisi sulla carreggiata.',
      lat: '41.883290',
      lon: '12.479540',
      ttlMin: 90,
      note: 'Fallen tree branch on right shoulder',
    },
    {
      id: 'pothole',
      icon: 'assets/road_events/pothole.png',
      en: 'Pothole',
      it: 'Buca',
      transportEn: 'Relays + MeshCore LoRa',
      transportIt: 'Relay + MeshCore LoRa',
      descEn: 'Severe road-surface damage flagged by drivers and cyclists so others avoid bent rims and flat tyres.',
      descIt: 'Buche profonde e asfalto dissestato segnalati da automobilisti e ciclisti per evitare danni.',
      lat: '41.888740',
      lon: '12.495610',
      ttlMin: 360,
      note: 'Deep pothole near bus stop lane',
    },
  ];

  const THEME_LABELS = {
    'dark-nostr': 'Dark · Nostr Violet (#8B5CF6)',
    'dark-bitcoin': 'Dark · Bitcoin Orange (#F7931A)',
    'light-nostr': 'Light · Nostr Violet (#8B5CF6)',
    'light-bitcoin': 'Light · Bitcoin Orange (#F7931A)',
  };

  /* ------------------------------------------------------------------------
     3. State & Initialization
     ------------------------------------------------------------------------ */
  let currentLang = localStorage.getItem('roadstr_lang') || (navigator.language?.startsWith('it') ? 'it' : 'en');
  let currentTheme = localStorage.getItem('roadstr_theme') || 'dark-nostr';
  let activeEventId = ROAD_EVENTS[0].id;
  let activeScreenIdx = 0;
  let screenTimer = null;
  let lightboxIdx = 0;

  const htmlEl = document.documentElement;
  const themeMeta = document.querySelector('meta[name="theme-color"]');

  function applyTheme(themeId) {
    if (!THEME_LABELS[themeId]) themeId = 'dark-nostr';
    currentTheme = themeId;
    htmlEl.setAttribute('data-theme', themeId);
    localStorage.setItem('roadstr_theme', themeId);

    const isDark = themeId.startsWith('dark');
    const accent = themeId.endsWith('bitcoin') ? 'bitcoin' : 'nostr';

    if (themeMeta) {
      themeMeta.setAttribute('content', isDark ? '#0D0D1A' : '#F5F5F5');
    }

    document.querySelectorAll('.accent-btn').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.accent === accent);
    });

    document.querySelectorAll('.theme-preset-btn').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.preset === themeId);
    });

    const labelEl = document.getElementById('active-theme-label');
    if (labelEl) labelEl.textContent = THEME_LABELS[themeId];
  }

  function applyLanguage(lang) {
    currentLang = lang === 'it' ? 'it' : 'en';
    htmlEl.setAttribute('lang', currentLang);
    localStorage.setItem('roadstr_lang', currentLang);

    const dict = TRANSLATIONS[currentLang];
    document.querySelectorAll('[data-i18n]').forEach((el) => {
      const key = el.getAttribute('data-i18n');
      if (dict[key]) el.textContent = dict[key];
    });

    const langBtn = document.getElementById('lang-toggle');
    if (langBtn) langBtn.textContent = currentLang.toUpperCase();

    renderRoadEventTiles();
    renderEventInspector(activeEventId);
  }

  /* ------------------------------------------------------------------------
     4. Theme & Language Controls
     ------------------------------------------------------------------------ */
  document.querySelectorAll('.accent-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const mode = currentTheme.startsWith('dark') ? 'dark' : 'light';
      applyTheme(`${mode}-${btn.dataset.accent}`);
    });
  });

  document.getElementById('mode-toggle')?.addEventListener('click', () => {
    const isDark = currentTheme.startsWith('dark');
    const accent = currentTheme.endsWith('bitcoin') ? 'bitcoin' : 'nostr';
    applyTheme(`${isDark ? 'light' : 'dark'}-${accent}`);
  });

  document.querySelectorAll('.theme-preset-btn').forEach((btn) => {
    btn.addEventListener('click', () => applyTheme(btn.dataset.preset));
  });

  document.getElementById('lang-toggle')?.addEventListener('click', () => {
    applyLanguage(currentLang === 'en' ? 'it' : 'en');
  });

  /* ------------------------------------------------------------------------
     5. Mobile Menu Drawer
     ------------------------------------------------------------------------ */
  const mobileMenuBtn = document.getElementById('mobile-menu-btn');
  const mobileDrawer = document.getElementById('mobile-drawer');

  mobileMenuBtn?.addEventListener('click', () => {
    const isOpen = !mobileDrawer.hidden;
    mobileDrawer.hidden = isOpen;
    mobileMenuBtn.setAttribute('aria-expanded', String(!isOpen));
  });

  mobileDrawer?.querySelectorAll('a').forEach((link) => {
    link.addEventListener('click', () => {
      mobileDrawer.hidden = true;
      mobileMenuBtn?.setAttribute('aria-expanded', 'false');
    });
  });

  /* ------------------------------------------------------------------------
     6. Hero Phone Interactive Screen Switcher
     ------------------------------------------------------------------------ */
  const phoneImgs = document.querySelectorAll('.phone-img');
  const phoneTabs = document.querySelectorAll('.phone-tab');

  function setHeroScreen(idx) {
    activeScreenIdx = idx % phoneImgs.length;
    phoneImgs.forEach((img, i) => img.classList.toggle('active', i === activeScreenIdx));
    phoneTabs.forEach((tab, i) => {
      const active = i === activeScreenIdx;
      tab.classList.toggle('active', active);
      tab.setAttribute('aria-selected', String(active));
    });
  }

  phoneTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      setHeroScreen(Number(tab.dataset.target));
      clearInterval(screenTimer);
      screenTimer = setInterval(() => setHeroScreen(activeScreenIdx + 1), 5200);
    });
  });

  screenTimer = setInterval(() => setHeroScreen(activeScreenIdx + 1), 5200);

  /* ------------------------------------------------------------------------
     7. Interactive Road Events (Nostr kind 1315) Explorer
     ------------------------------------------------------------------------ */
  const eventsGrid = document.getElementById('events-grid');

  function renderRoadEventTiles() {
    if (!eventsGrid) return;
    eventsGrid.innerHTML = '';
    ROAD_EVENTS.forEach((ev) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = `event-tile${ev.id === activeEventId ? ' active' : ''}`;
      btn.setAttribute('role', 'option');
      btn.setAttribute('aria-selected', String(ev.id === activeEventId));
      btn.innerHTML = `<img src="${ev.icon}" alt="${ev[currentLang]}" loading="lazy"><span>${ev[currentLang]}</span>`;
      btn.addEventListener('click', () => {
        activeEventId = ev.id;
        renderRoadEventTiles();
        renderEventInspector(ev.id);
      });
      eventsGrid.appendChild(btn);
    });
  }

  function renderEventInspector(eventId) {
    const ev = ROAD_EVENTS.find((e) => e.id === eventId) || ROAD_EVENTS[0];
    const now = Math.floor(Date.now() / 1000);
    const exp = now + ev.ttlMin * 60;

    document.getElementById('inspector-icon').src = ev.icon;
    document.getElementById('inspector-icon').alt = ev[currentLang];
    document.getElementById('inspector-name').textContent = ev[currentLang];
    document.getElementById('inspector-transport').textContent =
      currentLang === 'it' ? ev.transportIt : ev.transportEn;
    document.getElementById('inspector-desc').textContent =
      currentLang === 'it' ? ev.descIt : ev.descEn;

    const sampleEvent = {
      kind: 1315,
      created_at: now,
      pubkey: '3bf0c63f...roadstr...signed_via_amber',
      tags: [
        ['t', 'roadstr'],
        ['c', ev.id],
        ['lat', ev.lat],
        ['lon', ev.lon],
        ['expiration', String(exp)],
      ],
      content: ev.note,
      sig: 'b94d27b9934d3e08a52e52d7da7dabfa...',
    };

    document.getElementById('inspector-json').textContent = JSON.stringify(sampleEvent, null, 2);
  }

  /* ------------------------------------------------------------------------
     8. Custom Vehicle & Ostrich Cursor Picker
     ------------------------------------------------------------------------ */
  const activeCursorImg = document.getElementById('active-cursor-sprite');
  const activeCursorName = document.getElementById('active-cursor-name');

  document.querySelectorAll('.cursor-opt').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.cursor-opt').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      const key = btn.dataset.cursor;
      if (activeCursorImg) activeCursorImg.src = `assets/cursors/${key}.png`;
      if (activeCursorName) activeCursorName.textContent = btn.dataset.label;
    });
  });

  /* ------------------------------------------------------------------------
     9. Screenshot Lightbox
     ------------------------------------------------------------------------ */
  const SHOTS = [
    { src: 'assets/screenshots/01-map.jpg', key: 'screens.s1Title' },
    { src: 'assets/screenshots/02-route.jpg', key: 'screens.s2Title' },
    { src: 'assets/screenshots/03-navigation.jpg', key: 'screens.s3Title' },
    { src: 'assets/screenshots/04-place.jpg', key: 'screens.s4Title' },
  ];

  const lightbox = document.getElementById('lightbox');
  const lightboxImg = document.getElementById('lightbox-img');
  const lightboxCaption = document.getElementById('lightbox-caption');

  function openLightbox(index) {
    lightboxIdx = (index + SHOTS.length) % SHOTS.length;
    const shot = SHOTS[lightboxIdx];
    lightboxImg.src = shot.src;
    lightboxCaption.textContent = TRANSLATIONS[currentLang][shot.key] || '';
    lightbox.hidden = false;
    document.body.style.overflow = 'hidden';
  }

  function closeLightbox() {
    lightbox.hidden = true;
    document.body.style.overflow = '';
  }

  document.querySelectorAll('.shot-card[data-lightbox]').forEach((card) => {
    card.addEventListener('click', () => openLightbox(Number(card.dataset.lightbox)));
  });

  document.getElementById('lightbox-close')?.addEventListener('click', closeLightbox);
  document.getElementById('lightbox-prev')?.addEventListener('click', () => openLightbox(lightboxIdx - 1));
  document.getElementById('lightbox-next')?.addEventListener('click', () => openLightbox(lightboxIdx + 1));

  lightbox?.addEventListener('click', (e) => {
    if (e.target === lightbox) closeLightbox();
  });

  document.addEventListener('keydown', (e) => {
    if (lightbox.hidden) return;
    if (e.key === 'Escape') closeLightbox();
    if (e.key === 'ArrowLeft') openLightbox(lightboxIdx - 1);
    if (e.key === 'ArrowRight') openLightbox(lightboxIdx + 1);
  });

  /* ------------------------------------------------------------------------
     10. Copy to Clipboard & Toast
     ------------------------------------------------------------------------ */
  const toastEl = document.getElementById('toast');
  let toastTimer = null;

  function showToast(msg) {
    if (!toastEl) return;
    toastEl.textContent = msg;
    toastEl.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toastEl.hidden = true;
    }, 2400);
  }

  document.querySelectorAll('.copy-btn').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const value = btn.dataset.copy || '';
      try {
        await navigator.clipboard.writeText(value);
        showToast(TRANSLATIONS[currentLang]['toast.copied']);
      } catch {
        showToast(value.slice(0, 24) + '…');
      }
    });
  });

  /* ------------------------------------------------------------------------
     11. Scroll Reveal Observer
     ------------------------------------------------------------------------ */
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.08, rootMargin: '0px 0px -30px 0px' }
  );

  document.querySelectorAll('.reveal').forEach((el) => observer.observe(el));

  /* Boot */
  applyTheme(currentTheme);
  applyLanguage(currentLang);
})();
