// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Italian (`it`).
class AppLocalizationsIt extends AppLocalizations {
  AppLocalizationsIt([String locale = 'it']) : super(locale);

  @override
  String get searchHint => 'Dove vuoi andare?';

  @override
  String get calculatingRoute => 'Calcolo percorso…';

  @override
  String get routeNotFoundTitle => 'Percorso non trovato';

  @override
  String get noRouteFound =>
      'Nessun percorso trovato. Controlla la connessione.';

  @override
  String get emptyServerResponse =>
      'Risposta vuota dal server. Controlla la connessione.';

  @override
  String routeError(String error) {
    return 'Errore calcolo percorso: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS non disponibile — Impostazioni → App → Roadstr → Permessi → Posizione';

  @override
  String get acquiringGps => 'Acquisizione GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Server GraphHopper non configurato — uso OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'API key GraphHopper non configurata — uso OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'API key OpenRouteService non configurata — uso OSRM';

  @override
  String get chooseRoute => 'Scegli percorso';

  @override
  String routeOptionsCount(int count) {
    return '$count opzioni';
  }

  @override
  String get cancel => 'Annulla';

  @override
  String get startNavigation => 'Avvia navigazione';

  @override
  String get fastestRoute => 'Più rapido';

  @override
  String get now => 'Ora';

  @override
  String get history => 'Cronologia';

  @override
  String get clearHistory => 'Cancella';

  @override
  String get selectedPosition => 'Posizione selezionata';

  @override
  String get bottomBarProfile => 'Profilo';

  @override
  String get bottomBarMenu => 'Menu';

  @override
  String get settingsTitle => 'Impostazioni';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Mappa';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Lingua';

  @override
  String get themeLightNostr => 'Chiaro · Viola Nostr';

  @override
  String get themeLightBitcoin => 'Chiaro · Arancione Bitcoin';

  @override
  String get themeDarkNostr => 'Scuro · Viola Nostr';

  @override
  String get themeDarkBitcoin => 'Scuro · Arancione Bitcoin';

  @override
  String get langSystem => 'Predefinito di sistema';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Mantieni schermo acceso';

  @override
  String get keepScreenOnDescription =>
      'Impedisce lo spegnimento durante la navigazione';

  @override
  String get rotateMap => 'Mappa segue direzione';

  @override
  String get rotateMapDescription =>
      'La mappa ruota in base alla direzione di marcia';

  @override
  String get mapTileUrlLabel => 'URL Tile mappa';

  @override
  String get routingProviderLabel => 'Routing provider';

  @override
  String get osrmProvider => 'OSRM (pubblico, senza chiave)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (locale/privato)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API key)';

  @override
  String get openrouteProvider => 'OpenRouteService (API key)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API key (opzionale)';

  @override
  String get verify => 'Verifica';

  @override
  String get graphhopperServerUrlRequired =>
      'Inserisci l\'URL del server prima di verificare.';

  @override
  String get successTitle => 'Successo';

  @override
  String get graphhopperServerReachable => 'Server GraphHopper raggiungibile';

  @override
  String get errorTitle => 'Errore';

  @override
  String get close => 'Chiudi';

  @override
  String get infoVersion => 'Versione';

  @override
  String get infoProtocol => 'Protocollo';

  @override
  String get infoMaps => 'Mappe';

  @override
  String get infoRouting => 'Routing';

  @override
  String get infoSource => 'Sorgente';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (self-hosted)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profilo';

  @override
  String get notConnected => 'Non connesso';

  @override
  String get loginWithNostrTitle => 'ACCEDI CON NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'La chiave privata non lascia mai il dispositivo · Metodo consigliato';

  @override
  String get nsecLoginOption => 'Inserisci nsec';

  @override
  String get nsecLoginDescription =>
      'Chiave privata salvata localmente · Meno sicuro';

  @override
  String get connectedViaAmber => 'Connesso via Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Connesso via nsec';

  @override
  String get publicKeyLabel => 'CHIAVE PUBBLICA';

  @override
  String get npubCopiedToClipboard => 'npub copiata negli appunti';

  @override
  String get logoutTitle => 'Disconnetti';

  @override
  String get logoutConfirmation =>
      'Vuoi rimuovere le credenziali Nostr da questo dispositivo?';

  @override
  String get logoutButton => 'Disconnetti';

  @override
  String get nostrIdentityInfo =>
      'Con un\'identità Nostr potrai pubblicare alert di traffico, segnalazioni e punti di interesse in modo decentralizzato sulla rete Nostr, senza server centrali.';

  @override
  String get warningTitle => 'Attenzione';

  @override
  String get nsecWarning =>
      'Stai per rawdoggare la tua chiave privata Nostr direttamente in un\'app. Chiunque abbia accesso fisico o remoto al tuo dispositivo potrebbe leggerla e prendere il controllo permanente della tua identità Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  Il metodo sicuro è Amber (NIP-55): la nsec non lascia mai il vault dell\'app signer.';

  @override
  String get nsecRiskAcknowledgment =>
      'Ho capito il rischio e voglio continuare comunque';

  @override
  String get continueButton => 'Continua';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber è un app signer Android conforme a NIP-55. La tua chiave privata rimane isolata dentro Amber e non viene mai condivisa.';

  @override
  String get requestKeyFromAmber => 'Richiedi chiave ad Amber';

  @override
  String get amberNotFound =>
      'Amber non trovato. Installalo dal Play Store o inserisci la npub manualmente.';

  @override
  String get waitingForAmberResponse => 'In attesa della risposta di Amber…';

  @override
  String get pasteNpubManually => 'Incolla la npub manualmente:';

  @override
  String get confirmNpub => 'Conferma npub';

  @override
  String get enterNsecTitle => 'Inserisci nsec';

  @override
  String get loginButton => 'Accedi';

  @override
  String get invalidNpubTitle => 'npub non valida';

  @override
  String get invalidNsecTitle => 'nsec non valida';

  @override
  String get invalidNpubMessage =>
      'Verifica di aver incollato la chiave corretta.';

  @override
  String get invalidNsecMessage =>
      'Verifica di aver incollato la chiave corretta.';

  @override
  String get amberResponseError => 'Errore risposta Amber';

  @override
  String get ok => 'OK';

  @override
  String get or => 'oppure';

  @override
  String get gpsNotActiveTitle => 'GPS non attivo';

  @override
  String get gpsDisabledMessage =>
      'Attiva il GPS nelle impostazioni del dispositivo per ottenere la tua posizione e usare la navigazione.';

  @override
  String get openSettings => 'Impostazioni';

  @override
  String get myLocation => 'La mia posizione';

  @override
  String get loginToReport =>
      'Accedi con Nostr (sezione Profilo) per segnalare eventi';

  @override
  String get navigateHere => 'Naviga qui';

  @override
  String get reportEventHere => 'Segnala evento qui';

  @override
  String get zapSendSats => 'Zap ⚡ (manda sats)';

  @override
  String get sendZap => 'Manda un Zap';

  @override
  String get chooseAmountSats => 'Scegli l\'importo in satoshi (sats):';

  @override
  String get customAmount => 'Importo personalizzato…';

  @override
  String get zapSending => 'Invio…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Recupero Lightning address…';

  @override
  String get noLightningAddress =>
      'Il segnalatore non ha una Lightning address';

  @override
  String get requestingInvoice => 'Richiedo invoice…';

  @override
  String get lnurlUnavailable => 'LNURL non disponibile';

  @override
  String get invoiceFailed => 'Impossibile generare invoice';

  @override
  String get openingWallet => 'Apertura wallet…';

  @override
  String get payingViaNwc => 'Pagamento via NWC…';

  @override
  String get noLightningWallet => 'Nessun wallet Lightning trovato';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sat inviati!';
  }

  @override
  String get stillThere => 'C\'è ancora';

  @override
  String get notThereAnymore => 'Non c\'è più';

  @override
  String get loginToConfirm => 'Accedi con Nostr per confermare o smentire';

  @override
  String get reportAnEvent => 'Segnala un evento';

  @override
  String get optionalComment => 'Commento opzionale…';

  @override
  String get publish => 'Pubblica';

  @override
  String get publishing => 'Pubblicazione…';

  @override
  String get reportPublished => 'Segnalazione pubblicata ✓';

  @override
  String get myReports => 'LE MIE SEGNALAZIONI';

  @override
  String get noReportsYet => 'Nessuna segnalazione pubblicata';

  @override
  String get zapBalance => 'Balance Zap';

  @override
  String get satoshiFromReports => 'Satoshi ricevuti dalle tue segnalazioni';

  @override
  String get reputationHigh => 'Alta';

  @override
  String get reputationMedium => 'Media';

  @override
  String get reputationLow => 'Bassa';

  @override
  String reputationLabel(String level) {
    return 'Reputazione $level';
  }

  @override
  String reliability(int pct) {
    return 'Affidabilità: $pct%';
  }

  @override
  String get confirmedLabel => 'Confermato';

  @override
  String get removedLabel => 'Rimosso';

  @override
  String get positionLabel => 'Posizione';

  @override
  String get loadingLabel => 'Caricamento…';

  @override
  String get sectionWebSearch => 'Ricerca web';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Incolla l\'URI NWC del tuo wallet (Alby Hub, Mutiny, Cashu…) per pagare i Zap direttamente dall\'app.';

  @override
  String get searchEngineQwantDesc =>
      'Europeo, privacy-first. Nessun tracciamento, nessun profilo pubblicitario. Scelta consigliata.';

  @override
  String get searchEngineBraveDesc =>
      'Indice indipendente, open-source. Non dipende da Google o Bing. Zero profilazione.';

  @override
  String get searchEngineDdgDesc =>
      'Privacy-focused e popolare. I risultati arrivano in parte da Bing — tienilo a mente.';

  @override
  String get searchEngineStartpageDesc =>
      'Risultati Google senza cedere a Google i tuoi dati. Un compromesso ragionevole.';

  @override
  String get searchEngineGoogleDesc =>
      'Efficacissimo. Ricorda però che Google ti conosce meglio di tua madre e rivende tutto agli inserzionisti. Ma vabbè, scelta tua. 🍪';

  @override
  String get categoryPolice => 'Polizia';

  @override
  String get categorySpeedCamera => 'Autovelox';

  @override
  String get categoryTrafficJam => 'Traffico';

  @override
  String get categoryAccident => 'Incidente';

  @override
  String get categoryRoadClosure => 'Strada chiusa';

  @override
  String get categoryConstruction => 'Cantiere';

  @override
  String get categoryHazard => 'Pericolo';

  @override
  String get categoryRoadCondition => 'Condiz. strada';

  @override
  String get categoryPothole => 'Buca';

  @override
  String get categoryFog => 'Nebbia';

  @override
  String get categoryIce => 'Ghiaccio';

  @override
  String get categoryAnimal => 'Animale';

  @override
  String get categoryOther => 'Altro';

  @override
  String get dateTimeLabel => 'Data / ora';

  @override
  String minutesAgo(int count) {
    return '$count min fa';
  }

  @override
  String hoursAgo(int count) {
    return '${count}h fa';
  }

  @override
  String daysAgo(int count) {
    return '${count}gg fa';
  }

  @override
  String get sectionFavorites => 'Luoghi preferiti';

  @override
  String get addFavorite => 'Aggiungi luogo';

  @override
  String get favoriteLabelHint => 'Nome (es. Casa, Ufficio)';

  @override
  String get favoriteAddressHint => 'Indirizzo';

  @override
  String get favoriteGeocodingError =>
      'Indirizzo non trovato. Prova con un indirizzo più preciso.';

  @override
  String get trafficAlertTitle => 'Nuovo traffico sul percorso';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category segnalato $age sul tuo percorso.\n\nVuoi trovare un percorso alternativo?';
  }

  @override
  String get trafficContinue => 'Continua';

  @override
  String get trafficRecalculate => 'Ricalcola percorso';

  @override
  String get navExitTitle => 'Uscire dalla navigazione?';

  @override
  String get navExitBody =>
      'Vuoi interrompere la navigazione e tornare alla mappa?';

  @override
  String get navContinue => 'Continua la navigazione';

  @override
  String get navExit => 'Sì, esci';

  @override
  String get loadingInfo => 'Caricamento informazioni…';

  @override
  String get conditionsOnRoute => 'Condizioni sul percorso';

  @override
  String get calculateRoute => 'Calcola percorso';

  @override
  String get sectionNavigationVoice => 'Voce di navigazione';

  @override
  String get voiceGuidance => 'Guida vocale';

  @override
  String get voiceGuidanceDesc =>
      'Leggi le istruzioni di svolta ad alta voce durante la navigazione';

  @override
  String get testVoiceEngine => 'Testa motore vocale';

  @override
  String get testVoiceEngineDesc =>
      'Tocca per verificare il motore TTS e ottenere le istruzioni di configurazione';

  @override
  String get ttsDialogTitle => 'Motore voce mancante';

  @override
  String get ttsDialogBody =>
      'Nessun motore Text-to-Speech funzionante trovato.\n\nRoadstr si appoggia solo a software open source — installa uno di questi motori gratuiti da F-Droid:';

  @override
  String get ttsRhvoiceDesc => 'Voce naturale, elenco lingue limitato';

  @override
  String get ttsEspeakDesc => 'Copre oltre 100 lingue, voce robotica';

  @override
  String get ttsInstallNote =>
      '⚠️ Dopo l\'installazione:\n1. Impostazioni Android → Accessibilità → Sintesi vocale\n2. Seleziona il motore appena installato\n3. Scarica i dati vocali della tua lingua\n4. Riavvia Roadstr completamente';

  @override
  String get ttsTestNow => 'Testa ora';

  @override
  String get voiceUnsupportedTitle => 'Guida vocale non disponibile';

  @override
  String get voiceUnsupportedBody =>
      'La tua lingua non è ancora supportata per le indicazioni vocali. Le istruzioni di navigazione continueranno comunque a comparire come testo a schermo.';

  @override
  String get kokoroModelTitle => 'Modello vocale (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Non scaricato · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Scaricamento...';

  @override
  String get kokoroModelStatusReady => 'Modello vocale pronto';

  @override
  String get kokoroModelDownloadBtn => 'Scarica';

  @override
  String get kokoroModelSupportedLangs =>
      'Supporta: italiano, inglese, spagnolo, francese, giapponese, cinese, portoghese';

  @override
  String get autoDarkMode => 'Tema scuro automatico';

  @override
  String get autoDarkModeDesc => 'Attiva il tema scuro al tramonto e all\'alba';

  @override
  String get settingsImperialUnits => 'Unità imperiali';

  @override
  String get settingsImperialUnitsDesc =>
      'Miglia e piedi al posto di chilometri e metri';

  @override
  String get arrivedTitle => '🎉 Sei arrivato!';

  @override
  String get arrivedBody => 'Hai raggiunto la tua destinazione.';

  @override
  String get arrivedFeedbackPrompt => 'Com\'è andata?';

  @override
  String get feedbackBad => 'Male';

  @override
  String get feedbackGood => 'Bene!';

  @override
  String get feedbackDialogTitle => 'Raccontaci cosa è andato storto';

  @override
  String get feedbackHint => 'Descrivi il problema…';

  @override
  String get feedbackSent => 'Feedback inviato — grazie! 🙏';

  @override
  String get feedbackSubmit => 'Invia';

  @override
  String get transportModeCar => 'Auto';

  @override
  String get transportModeWalk => 'A piedi';

  @override
  String etaArrivalLabel(String time) {
    return 'Arr. $time';
  }

  @override
  String get supportRoadstr => 'Supporta Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address copiato negli appunti';
  }

  @override
  String get disclaimerTitle => 'Avviso importante';

  @override
  String get disclaimerAccept => 'Ho letto e accetto';

  @override
  String get disclaimerBody =>
      'Roadstr è un\'applicazione di navigazione sperimentale basata su dati OpenStreetMap e sul protocollo Nostr. Utilizzando questa app l\'utente accetta integralmente le seguenti condizioni:\n\n🚗  SICUREZZA STRADALE\nIl guidatore è tenuto a mantenere sempre gli occhi sulla strada. Non guardare il telefono durante la guida. Assicurare il dispositivo in un supporto approvato e visibile senza distogliere l\'attenzione dalla strada.\n\n⚠️  LIMITAZIONE DI RESPONSABILITÀ\nRoadstr viene fornita \"così com\'è\", senza garanzie di accuratezza, completezza o idoneità a scopi specifici. Gli sviluppatori declinano qualsiasi responsabilità per danni derivanti dall\'uso dell\'applicazione, inclusi ma non limitati a: incidenti stradali, sanzioni amministrative, danni a cose o persone.\n\n🚫  ZONE A TRAFFICO LIMITATO (ZTL)\nLa navigazione si basa sui dati OpenStreetMap che potrebbero non essere aggiornati riguardo a ZTL, corsie preferenziali e restrizioni locali. L\'utente è responsabile di verificare autonomamente l\'accessibilità del percorso suggerito nelle zone a traffico limitato prima di percorrerlo. Gli sviluppatori non rispondono di eventuali sanzioni ricevute.\n\n📍  ACCURATEZZA\nIl tracciamento GPS può risultare impreciso. Le indicazioni stradali sono a titolo orientativo. Rispettare sempre la segnaletica stradale verticale e orizzontale, che ha sempre la precedenza sulle indicazioni dell\'app.\n\n🔒  PRIVACY\nNessun dato di posizione viene trasmesso a server esterni. Il calcolo del percorso avviene tramite servizi terzi (OSRM, GraphHopper, OpenRouteService) ai quali vengono inviate esclusivamente le coordinate di partenza e arrivo.\n\nUtilizzando Roadstr l\'utente si assume la piena e totale responsabilità dell\'utilizzo dell\'applicazione e di qualsiasi conseguenza derivante dall\'uso della stessa.';

  @override
  String get readOnWikipedia => 'Leggi su Wikipedia';

  @override
  String searchOnEngine(String engine) {
    return 'Cerca su $engine';
  }

  @override
  String get plannerFromHint => 'Da…';

  @override
  String get plannerToHint => 'Destinazione…';

  @override
  String departEta(String dep, String arr) {
    return 'Partenza $dep  →  Arrivo $arr';
  }

  @override
  String get modeCar => 'Auto';

  @override
  String get modeBike => 'Bici';

  @override
  String get modeWalk => 'A piedi';

  @override
  String windSpeed(String speed) {
    return 'vento $speed km/h';
  }

  @override
  String durationMin(int m) {
    return '$m min';
  }

  @override
  String durationHourMin(int h, int m) {
    return '${h}h ${m}min';
  }

  @override
  String get weatherClear => 'Sereno';

  @override
  String get weatherPartlyCloudy => 'Parz. nuvoloso';

  @override
  String get weatherCloudy => 'Nuvoloso';

  @override
  String get weatherFog => 'Nebbia';

  @override
  String get weatherLightRain => 'Pioggia leggera';

  @override
  String get weatherRain => 'Pioggia';

  @override
  String get weatherSnow => 'Neve';

  @override
  String get weatherShowers => 'Rovesci';

  @override
  String get weatherThunderstorm => 'Temporale';

  @override
  String get ztlAheadWarning =>
      'ZTL in arrivo — percorso in zona a traffico limitato';

  @override
  String get ztlInsideWarning => 'Zona a Traffico Limitato';

  @override
  String get onboardingAppSubtitle => 'Navigazione Nostr open-source';

  @override
  String get onboardingWelcomeTitle => 'Benvenuto';

  @override
  String get onboardingWelcomeBody =>
      'Tutto ciò che serve per navigare — senza rinunciare alla tua privacy.';

  @override
  String get onboardingFeatureNav => 'Navigazione GPS passo per passo';

  @override
  String get onboardingFeatureNostr =>
      'Segnalazioni stradali Nostr (autovelox, pericoli, traffico)';

  @override
  String get onboardingFeatureLightning =>
      'Mance Lightning Network per chi segnala eventi';

  @override
  String get onboardingFeatureVoice => 'Guida vocale AI on-device (Kokoro-82M)';

  @override
  String get onboardingFeaturePrivacy =>
      'Nessun account richiesto — nessun tracciamento, mai';

  @override
  String get onboardingGetStarted => 'Inizia';

  @override
  String get onboardingNostrTitle => 'Identità Nostr';

  @override
  String get onboardingNostrSubtitle =>
      'Opzionale — collegati per segnalare eventi stradali, confermare avvisi e guadagnare mance Lightning.';

  @override
  String get onboardingNostrConnected => 'Connesso';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (consigliato)';

  @override
  String get onboardingAmberSubtitle =>
      'Collegati con l\'app firmataria Amber. La tua chiave privata non lascia mai Amber.';

  @override
  String get onboardingNsecTitle => 'Chiave nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Incolla la tua chiave privata. Salvata nell\'Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Chiave nsec non valida — controlla e riprova.';

  @override
  String get onboardingSkip => 'Salta per ora';

  @override
  String get onboardingContinue => 'Continua';

  @override
  String get onboardingEnterNsec => 'Inserisci la chiave nsec';

  @override
  String get onboardingSetupTitle => 'Configura Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configura l\'accesso alla posizione e la guida vocale opzionale.';

  @override
  String get onboardingLocationTitle => 'Posizione';

  @override
  String get onboardingLocationGranted => 'Accesso alla posizione concesso';

  @override
  String get onboardingLocationRequired => 'Necessaria per la navigazione GPS';

  @override
  String get onboardingGrantButton => 'Concedi';

  @override
  String get onboardingGrapheneTitle => 'Utenti GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Concedi la posizione Precisa (non Approssimata) e consenti l\'accesso Sempre (non solo durante l\'utilizzo) in:\nImpostazioni → App → Roadstr → Autorizzazioni → Posizione\n\nCon solo la posizione approssimata o \'solo durante l\'utilizzo\', la navigazione GPS perderà la posizione in background.';

  @override
  String get onboardingVoiceTitle => 'Guida vocale AI (opzionale)';

  @override
  String get onboardingVoiceReady => 'Modello vocale Kokoro pronto';

  @override
  String get onboardingVoiceDownloading =>
      'Download del modello vocale in corso…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Scarica il modello AI Kokoro (82 MB) per la voce on-device';

  @override
  String get onboardingVoiceChecking => 'Verifica dello stato del modello…';

  @override
  String get onboardingDownloadButton => 'Scarica';

  @override
  String get onboardingVoiceLaterHint =>
      'Puoi anche scaricare il modello vocale dopo da\nImpostazioni → Voce di navigazione.';

  @override
  String get onboardingReadyTitle => 'Sei pronto a navigare!';

  @override
  String get onboardingReadyBody =>
      'Roadstr aprirà la mappa.\nPuoi configurare tutto il resto dalle Impostazioni.';

  @override
  String get onboardingLetsGo => 'Andiamo!';

  @override
  String get onboardingProfileLoading => 'Caricamento profilo…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get onboardingVpnNotice =>
      'Per la massima privacy — le segnalazioni vengono propagate nella rete Nostr — è consigliato l\'uso di una VPN. Mullvad, pagabile in Bitcoin, è quella consigliata.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Per un funzionamento affidabile, imposta il permesso di localizzazione dell\'app su \"Consenti sempre\", non solo mentre l\'app è in uso.';
}
