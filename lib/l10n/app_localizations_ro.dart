// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Romanian Moldavian Moldovan (`ro`).
class AppLocalizationsRo extends AppLocalizations {
  AppLocalizationsRo([String locale = 'ro']) : super(locale);

  @override
  String get searchHint => 'Unde vrei să mergi?';

  @override
  String get calculatingRoute => 'Se calculează ruta…';

  @override
  String get routeNotFoundTitle => 'Ruta nu a fost găsită';

  @override
  String get noRouteFound => 'Nicio rută găsită. Verificați conexiunea.';

  @override
  String get emptyServerResponse =>
      'Răspuns gol de la server. Verificați conexiunea.';

  @override
  String routeError(String error) {
    return 'Eroare la calculul rutei: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS indisponibil — Setări → Aplicație → Roadstr → Permisiuni → Locație';

  @override
  String get acquiringGps => 'Se obține GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Serverul GraphHopper nu este configurat — se folosește OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'Cheia API GraphHopper nu este configurată — se folosește OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'Cheia API OpenRouteService nu este configurată — se folosește OSRM';

  @override
  String get chooseRoute => 'Alegeți ruta';

  @override
  String routeOptionsCount(int count) {
    return '$count opțiuni';
  }

  @override
  String get cancel => 'Anulare';

  @override
  String get startNavigation => 'Pornire navigare';

  @override
  String get fastestRoute => 'Cea mai rapidă';

  @override
  String get now => 'Acum';

  @override
  String get history => 'Istoric';

  @override
  String get clearHistory => 'Șterge';

  @override
  String get selectedPosition => 'Poziție selectată';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Meniu';

  @override
  String get settingsTitle => 'Setări';

  @override
  String get sectionTheme => 'Temă';

  @override
  String get sectionMap => 'Hartă';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Limbă';

  @override
  String get themeLightNostr => 'Deschis · Nostr Violet';

  @override
  String get themeLightBitcoin => 'Deschis · Bitcoin Portocaliu';

  @override
  String get themeDarkNostr => 'Întunecat · Nostr Violet';

  @override
  String get themeDarkBitcoin => 'Întunecat · Bitcoin Portocaliu';

  @override
  String get langSystem => 'Implicit sistem';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Mențineți ecranul activ';

  @override
  String get keepScreenOnDescription => 'Previne repausul în timpul navigării';

  @override
  String get rotateMap => 'Harta urmează direcția';

  @override
  String get rotateMapDescription =>
      'Harta se rotește în funcție de direcția de mers';

  @override
  String get mapTileUrlLabel => 'URL plăci hartă';

  @override
  String get routingProviderLabel => 'Furnizor de rutare';

  @override
  String get osrmProvider => 'OSRM (public, fără cheie)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (local/privat)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (cheie API)';

  @override
  String get openrouteProvider => 'OpenRouteService (cheie API)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'Cheie API GraphHopper (opțional)';

  @override
  String get verify => 'Verificați';

  @override
  String get graphhopperServerUrlRequired =>
      'Introduceți URL-ul serverului înainte de verificare.';

  @override
  String get successTitle => 'Succes';

  @override
  String get graphhopperServerReachable =>
      'Serverul GraphHopper este accesibil';

  @override
  String get errorTitle => 'Eroare';

  @override
  String get close => 'Închide';

  @override
  String get infoVersion => 'Versiune';

  @override
  String get infoProtocol => 'Protocol';

  @override
  String get infoMaps => 'Hărți';

  @override
  String get infoRouting => 'Rutare';

  @override
  String get infoSource => 'Sursă';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (auto-găzduit)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Neconectat';

  @override
  String get loginWithNostrTitle => 'CONECTARE CU NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Cheia privată nu părăsește niciodată dispozitivul · Recomandat';

  @override
  String get nsecLoginOption => 'Introduceți nsec';

  @override
  String get nsecLoginDescription =>
      'Cheia privată stocată local · Mai puțin sigur';

  @override
  String get connectedViaAmber => 'Conectat prin Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Conectat prin nsec';

  @override
  String get publicKeyLabel => 'CHEIE PUBLICĂ';

  @override
  String get npubCopiedToClipboard => 'npub copiat în clipboard';

  @override
  String get logoutTitle => 'Deconectare';

  @override
  String get logoutConfirmation =>
      'Eliminați credențialele Nostr de pe acest dispozitiv?';

  @override
  String get logoutButton => 'Deconectare';

  @override
  String get nostrIdentityInfo =>
      'Cu o identitate Nostr puteți publica alerte de trafic, rapoarte și puncte de interes într-un mod descentralizat în rețeaua Nostr, fără servere centrale.';

  @override
  String get warningTitle => 'Avertisment';

  @override
  String get nsecWarning =>
      'Urmează să introduceți cheia privată Nostr direct într-o aplicație. Oricine cu acces fizic sau de la distanță la dispozitivul dvs. ar putea-o citi și prelua permanent controlul asupra identității dvs. Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  Metoda sigură este Amber (NIP-55): nsec nu părăsește niciodată seiful semnatarului aplicației.';

  @override
  String get nsecRiskAcknowledgment =>
      'Înțeleg riscul și vreau să continui oricum';

  @override
  String get continueButton => 'Continuare';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber este un semnatar de aplicații Android conform NIP-55. Cheia dvs. privată rămâne izolată în Amber și nu este niciodată partajată.';

  @override
  String get requestKeyFromAmber => 'Solicitați cheia publică de la Amber';

  @override
  String get amberNotFound =>
      'Amber nu a fost găsit. Instalați-l din Play Store sau introduceți manual npub-ul.';

  @override
  String get waitingForAmberResponse => 'Se așteaptă răspunsul Amber…';

  @override
  String get pasteNpubManually => 'Lipiți npub-ul manual:';

  @override
  String get confirmNpub => 'Confirmați npub';

  @override
  String get enterNsecTitle => 'Introduceți nsec';

  @override
  String get loginButton => 'Conectare';

  @override
  String get invalidNpubTitle => 'npub invalid';

  @override
  String get invalidNsecTitle => 'nsec invalid';

  @override
  String get invalidNpubMessage => 'Asigurați-vă că ați lipit npub-ul corect.';

  @override
  String get invalidNsecMessage => 'Asigurați-vă că ați lipit nsec-ul corect.';

  @override
  String get amberResponseError => 'Eroare răspuns Amber';

  @override
  String get ok => 'OK';

  @override
  String get or => 'sau';

  @override
  String get gpsNotActiveTitle => 'GPS inactiv';

  @override
  String get gpsDisabledMessage =>
      'Activați GPS-ul din setările dispozitivului pentru a obține locația și a folosi navigarea.';

  @override
  String get openSettings => 'Setări';

  @override
  String get myLocation => 'Locația mea';

  @override
  String get loginToReport =>
      'Conectați-vă cu Nostr (secțiunea Profil) pentru a raporta evenimente';

  @override
  String get navigateHere => 'Navigați aici';

  @override
  String get reportEventHere => 'Raportați eveniment aici';

  @override
  String get zapSendSats => 'Zap ⚡ (trimiteți sats)';

  @override
  String get sendZap => 'Trimiteți un Zap';

  @override
  String get chooseAmountSats => 'Alegeți suma în satoshi (sats):';

  @override
  String get customAmount => 'Sumă personalizată…';

  @override
  String get zapSending => 'Se trimite…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Se obține adresa Lightning…';

  @override
  String get noLightningAddress => 'Acest reporter nu are adresă Lightning';

  @override
  String get requestingInvoice => 'Se solicită factura…';

  @override
  String get lnurlUnavailable => 'LNURL indisponibil';

  @override
  String get invoiceFailed => 'Imposibil de generat factura';

  @override
  String get openingWallet => 'Se deschide portofelul…';

  @override
  String get payingViaNwc => 'Plată prin NWC…';

  @override
  String get noLightningWallet => 'Niciun portofel Lightning găsit';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats trimiși!';
  }

  @override
  String get stillThere => 'Încă acolo';

  @override
  String get notThereAnymore => 'Nu mai este acolo';

  @override
  String get loginToConfirm =>
      'Conectați-vă cu Nostr pentru a confirma sau contesta';

  @override
  String get reportAnEvent => 'Raportați un eveniment';

  @override
  String get optionalComment => 'Comentariu opțional…';

  @override
  String get publish => 'Publicare';

  @override
  String get publishing => 'Se publică…';

  @override
  String get reportPublished => 'Raport publicat ✓';

  @override
  String get myReports => 'RAPOARTELE MELE';

  @override
  String get noReportsYet => 'Niciun raport publicat';

  @override
  String get zapBalance => 'Sold Zap';

  @override
  String get satoshiFromReports => 'Satoshi primiți din rapoartele dvs.';

  @override
  String get reputationHigh => 'Ridicată';

  @override
  String get reputationMedium => 'Medie';

  @override
  String get reputationLow => 'Scăzută';

  @override
  String reputationLabel(String level) {
    return 'Reputație $level';
  }

  @override
  String reliability(int pct) {
    return 'Fiabilitate: $pct%';
  }

  @override
  String get confirmedLabel => 'Confirmat';

  @override
  String get removedLabel => 'Eliminat';

  @override
  String get positionLabel => 'Poziție';

  @override
  String get loadingLabel => 'Se încarcă…';

  @override
  String get sectionWebSearch => 'Căutare web';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Lipiți URI-ul NWC (Alby Hub, Mutiny, Cashu…) pentru a plăti Zap-uri direct din aplicație.';

  @override
  String get searchEngineQwantDesc =>
      'European, confidențialitate pe primul loc. Fără urmărire, fără profiluri publicitare. Recomandat.';

  @override
  String get searchEngineBraveDesc =>
      'Index independent, open-source. Fără dependență de Google sau Bing. Zero profilare.';

  @override
  String get searchEngineDdgDesc =>
      'Axat pe confidențialitate și popular. Rezultate parțial din Bing — țineți cont de asta.';

  @override
  String get searchEngineStartpageDesc =>
      'Rezultate Google fără a trimite datele dvs. la Google. Un compromis rezonabil.';

  @override
  String get searchEngineGoogleDesc =>
      'Foarte eficient. Dar rețineți: Google vă cunoaște mai bine decât mama dvs. și vinde totul advertiserilor. Alegerea dvs. 🍪';

  @override
  String get categoryPolice => 'Poliție';

  @override
  String get categorySpeedCamera => 'Radar de viteză';

  @override
  String get categoryTrafficJam => 'Ambuteiaj';

  @override
  String get categoryAccident => 'Accident';

  @override
  String get categoryRoadClosure => 'Drum închis';

  @override
  String get categoryConstruction => 'Lucrări';

  @override
  String get categoryHazard => 'Pericol';

  @override
  String get categoryRoadCondition => 'Starea drumului';

  @override
  String get categoryPothole => 'Groapă';

  @override
  String get categoryFog => 'Ceață';

  @override
  String get categoryIce => 'Gheață';

  @override
  String get categoryAnimal => 'Animal';

  @override
  String get categoryOther => 'Altele';

  @override
  String get dateTimeLabel => 'Dată / oră';

  @override
  String minutesAgo(int count) {
    return 'acum $count min';
  }

  @override
  String hoursAgo(int count) {
    return 'acum ${count}h';
  }

  @override
  String daysAgo(int count) {
    return 'acum ${count}z';
  }

  @override
  String get sectionFavorites => 'Locuri salvate';

  @override
  String get addFavorite => 'Adaugă loc';

  @override
  String get favoriteLabelHint => 'Nume (ex. Acasă, Birou)';

  @override
  String get favoriteAddressHint => 'Adresă';

  @override
  String get favoriteGeocodingError =>
      'Adresa nu a fost găsită. Încearcă o adresă mai specifică.';

  @override
  String get trafficAlertTitle => 'Trafic nou pe rută';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category raportat $age pe ruta dvs.\n\nDoriți să găsiți o rută alternativă?';
  }

  @override
  String get trafficContinue => 'Continuați';

  @override
  String get trafficRecalculate => 'Recalculați ruta';

  @override
  String get navExitTitle => 'Ieșiți din navigare?';

  @override
  String get navExitBody =>
      'Doriți să opriți navigarea și să reveniți la hartă?';

  @override
  String get navContinue => 'Continuați navigarea';

  @override
  String get navExit => 'Da, ieșiți';

  @override
  String get loadingInfo => 'Se încarcă informațiile…';

  @override
  String get conditionsOnRoute => 'Condiții pe rută';

  @override
  String get calculateRoute => 'Calculați ruta';

  @override
  String get sectionNavigationVoice => 'Voce de navigare';

  @override
  String get voiceGuidance => 'Ghidare vocală';

  @override
  String get voiceGuidanceDesc =>
      'Citește cu voce tare instrucțiunile de virare în timpul navigării';

  @override
  String get testVoiceEngine => 'Testează motorul vocal';

  @override
  String get testVoiceEngineDesc =>
      'Atinge pentru a verifica motorul TTS și a obține instrucțiuni de configurare';

  @override
  String get ttsDialogTitle => 'Lipsește motorul vocal';

  @override
  String get ttsDialogBody =>
      'Nu a fost găsit niciun motor Text-to-Speech funcțional.\n\nRoadstr se bazează exclusiv pe software open source — instalează unul dintre aceste motoare gratuite de pe F-Droid:';

  @override
  String get ttsRhvoiceDesc => 'Voce cu sunet natural, listă limitată de limbi';

  @override
  String get ttsEspeakDesc => 'Acoperă peste 100 de limbi, voce robotică';

  @override
  String get ttsInstallNote =>
      '⚠️ După instalare:\n1. Setări Android → Accesibilitate → Conversie text în vorbire\n2. Selectează motorul instalat recent\n3. Descarcă datele vocale pentru limba ta\n4. Repornește Roadstr complet';

  @override
  String get ttsTestNow => 'Testează acum';

  @override
  String get voiceUnsupportedTitle => 'Ghidarea vocală nu este disponibilă';

  @override
  String get voiceUnsupportedBody =>
      'Limba ta nu este încă acceptată pentru indicații de virare vorbite. Instrucțiunile de navigare vor continua să apară ca text pe ecran.';

  @override
  String get kokoroModelTitle => 'Model vocal (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Nedescarcat · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Se descarcă...';

  @override
  String get kokoroModelStatusReady => 'Modelul vocal este pregătit';

  @override
  String get kokoroModelDownloadBtn => 'Descarcă';

  @override
  String get kokoroModelSupportedLangs =>
      'Suportă: italiană, engleză, spaniolă, franceză, japoneză, chineză, portugheză';

  @override
  String get autoDarkMode => 'Temă întunecată automată';

  @override
  String get autoDarkModeDesc => 'Activează tema întunecată la apus';

  @override
  String get settingsImperialUnits => 'Unități imperiale';

  @override
  String get settingsImperialUnitsDesc =>
      'Mile și picioare în loc de kilometri și metri';

  @override
  String get arrivedTitle => '🎉 Ați ajuns!';

  @override
  String get arrivedBody => 'Ați ajuns la destinație.';

  @override
  String get arrivedFeedbackPrompt => 'Cum a mers?';

  @override
  String get feedbackBad => 'Rău';

  @override
  String get feedbackGood => 'Bine!';

  @override
  String get feedbackDialogTitle => 'Spuneți-ne ce a mers prost';

  @override
  String get feedbackHint => 'Descrieți problema…';

  @override
  String get feedbackSent => 'Feedback trimis — mulțumim! 🙏';

  @override
  String get feedbackSubmit => 'Trimite';

  @override
  String get transportModeCar => 'Mașină';

  @override
  String get transportModeWalk => 'Pe jos';

  @override
  String etaArrivalLabel(String time) {
    return 'Sos. $time';
  }

  @override
  String get supportRoadstr => 'Sprijiniți Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address copiat în clipboard';
  }

  @override
  String get disclaimerTitle => 'Notificare importantă';

  @override
  String get disclaimerAccept => 'Am citit și accept';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Citește pe Wikipedia';

  @override
  String searchOnEngine(String engine) {
    return 'Caută pe $engine';
  }

  @override
  String get plannerFromHint => 'De la…';

  @override
  String get plannerToHint => 'Destinație…';

  @override
  String departEta(String dep, String arr) {
    return 'Plecare $dep  →  Sosire $arr';
  }

  @override
  String get modeCar => 'Mașină';

  @override
  String get modeBike => 'Bicicletă';

  @override
  String get modeWalk => 'Pe jos';

  @override
  String windSpeed(String speed) {
    return 'vânt $speed km/h';
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
  String get weatherClear => 'Senin';

  @override
  String get weatherPartlyCloudy => 'Parțial noros';

  @override
  String get weatherCloudy => 'Noros';

  @override
  String get weatherFog => 'Ceață';

  @override
  String get weatherLightRain => 'Ploaie ușoară';

  @override
  String get weatherRain => 'Ploaie';

  @override
  String get weatherSnow => 'Zăpadă';

  @override
  String get weatherShowers => 'Averse';

  @override
  String get weatherThunderstorm => 'Furtună';

  @override
  String get ztlAheadWarning =>
      'Zonă ZTL înainte — ruta intră în zonă restricționată';

  @override
  String get ztlInsideWarning => 'Zonă cu trafic restricționat';

  @override
  String get onboardingAppSubtitle => 'Navigare Nostr open-source';

  @override
  String get onboardingWelcomeTitle => 'Bun venit';

  @override
  String get onboardingWelcomeBody =>
      'Everything your navigation app needs — without giving up your privacy.';

  @override
  String get onboardingFeatureNav => 'Turn-by-turn GPS navigation';

  @override
  String get onboardingFeatureNostr =>
      'Nostr road events (speed cameras, hazards, traffic)';

  @override
  String get onboardingFeatureLightning =>
      'Lightning Network tips for event reporters';

  @override
  String get onboardingFeatureVoice =>
      'On-device AI voice guidance (Kokoro-82M)';

  @override
  String get onboardingFeaturePrivacy =>
      'No account required — no tracking, ever';

  @override
  String get onboardingGetStarted => 'Începeți';

  @override
  String get onboardingNostrTitle => 'Identitate Nostr';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Conectat';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (recomandat)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'Cheie nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Cheie nsec invalidă — verificați și încercați din nou.';

  @override
  String get onboardingSkip => 'Omiteți deocamdată';

  @override
  String get onboardingContinue => 'Continuați';

  @override
  String get onboardingEnterNsec => 'Introduceți cheia nsec';

  @override
  String get onboardingSetupTitle => 'Configurați Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Locație';

  @override
  String get onboardingLocationGranted => 'Acces la locație acordat';

  @override
  String get onboardingLocationRequired => 'Necesar pentru navigare GPS';

  @override
  String get onboardingGrantButton => 'Acordați';

  @override
  String get onboardingGrapheneTitle => 'Utilizatori GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'Ghidare vocală AI (opțional)';

  @override
  String get onboardingVoiceReady => 'Modelul vocal Kokoro este gata';

  @override
  String get onboardingVoiceDownloading => 'Se descarcă modelul vocal…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Descărcați';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Ești gata să navighezi!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Haideți!';

  @override
  String get onboardingProfileLoading => 'Se încarcă profilul…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Voice';

  @override
  String get kokoroVoiceFemale => 'Female';

  @override
  String get kokoroVoiceMale => 'Male';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Male voice not available for this language';

  @override
  String get kokoroSpeedTitle => 'Speech speed';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Optional: your saved favorites can sync across your devices through the Nostr relays, end-to-end encrypted (NIP-44) with your own key — relays only ever see ciphertext, and nobody but you can read the contents. Enable it anytime from Settings.';

  @override
  String get parkingSaveHere => 'Save parking here';

  @override
  String get parkingMarkerTitle => 'Parking spot';

  @override
  String get parkingNavigateHere => 'Navigate to parking';

  @override
  String get parkingRemove => 'Remove parking';

  @override
  String get parkingSavedSnack => 'Parking spot saved';

  @override
  String get parkingRemovedSnack => 'Parking spot removed';

  @override
  String get exportFavoritesTitle => 'Export favorites';

  @override
  String get exportFavoritesDesc =>
      'Save your favorite places to a JSON file you can back up or move to another device.';

  @override
  String get exportEncryptToggle => 'Encrypt with a password';

  @override
  String get exportPasswordHint => 'Password';

  @override
  String get exportButton => 'Export';

  @override
  String get exportSuccessSnack => 'Favorites exported';

  @override
  String get exportFailedSnack => 'Export failed';

  @override
  String get importFavoritesTitle => 'Import favorites';

  @override
  String get importButton => 'Import';

  @override
  String get importPasswordPrompt =>
      'This file is encrypted — enter the password';

  @override
  String importSuccessSnack(int n) {
    return '$n favorites imported';
  }

  @override
  String get importFailedSnack => 'Import failed — check the file and password';

  @override
  String get syncFavoritesTitle => 'Sync favorites';

  @override
  String get syncFavoritesDesc =>
      'Publish your favorites, end-to-end encrypted, to your Nostr relays so they follow you across devices. Relays only ever see ciphertext — nobody but you can read the contents.';

  @override
  String get syncFavoritesEnable => 'Enable sync';

  @override
  String get syncNowButton => 'Push to Nostr';

  @override
  String get syncPullButton => 'Pull from Nostr';

  @override
  String get syncPushingStatus => 'Publishing…';

  @override
  String get syncPullingStatus => 'Fetching…';

  @override
  String get syncSuccessSnack => 'Favorites synced';

  @override
  String get syncFailedSnack => 'Sync failed';

  @override
  String get syncNotAvailableAmber =>
      'Encrypted sync isn\'t available with Amber sign-in yet';

  @override
  String syncLastSyncLabel(String when) {
    return 'Last synced: $when';
  }

  @override
  String get syncNoIdentity =>
      'Sign in with Nostr (Settings → Profile) to enable sync';

  @override
  String get syncPullConfirmTitle => 'Replace local favorites?';

  @override
  String get syncPullConfirmBody =>
      'This will merge favorites fetched from Nostr with the ones already on this device.';

  @override
  String get onboardingVpnNotice =>
      'Pentru confidențialitate maximă — rapoartele se propagă în rețeaua Nostr — se recomandă utilizarea unui VPN. Mullvad, plătibil în Bitcoin, este alegerea recomandată.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Pentru o funcționare fiabilă, setați permisiunea de localizare a aplicației pe „Permite întotdeauna”, nu doar în timpul utilizării.';
}
