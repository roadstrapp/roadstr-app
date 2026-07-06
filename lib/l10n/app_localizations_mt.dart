// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Maltese (`mt`).
class AppLocalizationsMt extends AppLocalizations {
  AppLocalizationsMt([String locale = 'mt']) : super(locale);

  @override
  String get searchHint => 'Fejn trid tmur?';

  @override
  String get calculatingRoute => 'Qed nikkalkola r-rotta…';

  @override
  String get routeNotFoundTitle => 'Rotta ma nstabitx';

  @override
  String get noRouteFound =>
      'L-ebda rotta ma nstabet. Iċċekkja l-konnessjoni tiegħek.';

  @override
  String get emptyServerResponse =>
      'Risposta vojta mis-server. Iċċekkja l-konnessjoni tiegħek.';

  @override
  String routeError(String error) {
    return 'Żball fil-kalkolu tar-rotta: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS mhux disponibbli — Settings → App → Roadstr → Permissions → Location';

  @override
  String get acquiringGps => 'Qed nikseb GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Is-server GraphHopper mhux ikkonfigurat — tuża OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'Ċavetta API GraphHopper mhux ikkonfigurata — tuża OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'Ċavetta API OpenRouteService mhux ikkonfigurata — tuża OSRM';

  @override
  String get chooseRoute => 'Agħżel rotta';

  @override
  String routeOptionsCount(int count) {
    return '$count għażliet';
  }

  @override
  String get cancel => 'Ikkanċella';

  @override
  String get startNavigation => 'Ibda n-navigazzjoni';

  @override
  String get fastestRoute => 'L-aktar mgħaġġel';

  @override
  String get now => 'Issa';

  @override
  String get history => 'Storja';

  @override
  String get clearHistory => 'Ħassar';

  @override
  String get selectedPosition => 'Pożizzjoni magħżula';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Menu';

  @override
  String get settingsTitle => 'Settings';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Mappa';

  @override
  String get sectionInfo => 'Informazzjoni';

  @override
  String get sectionLanguage => 'Lingwa';

  @override
  String get themeLightNostr => 'Ċar · Nostr Vjola';

  @override
  String get themeLightBitcoin => 'Ċar · Bitcoin Oranġjo';

  @override
  String get themeDarkNostr => 'Skur · Nostr Vjola';

  @override
  String get themeDarkBitcoin => 'Skur · Bitcoin Oranġjo';

  @override
  String get langSystem => 'Default tas-sistema';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Żomm l-iskrin mixgħul';

  @override
  String get keepScreenOnDescription =>
      'Jimpedixxi l-irqad waqt in-navigazzjoni';

  @override
  String get rotateMap => 'Il-mappa ssegwi d-direzzjoni';

  @override
  String get rotateMapDescription =>
      'Il-mappa tirrotaw abbażi tad-direzzjoni tas-sewqan';

  @override
  String get mapTileUrlLabel => 'URL tal-tile tal-mappa';

  @override
  String get routingProviderLabel => 'Fornitur tal-routing';

  @override
  String get osrmProvider => 'OSRM (pubbliku, l-ebda ċavetta meħtieġa)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokali/privat)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (ċavetta API)';

  @override
  String get openrouteProvider => 'OpenRouteService (ċavetta API)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'Ċavetta API GraphHopper (fakultattiva)';

  @override
  String get verify => 'Ivverifika';

  @override
  String get graphhopperServerUrlRequired =>
      'Daħħal l-URL tas-server qabel ma tivverifika.';

  @override
  String get successTitle => 'Suċċess';

  @override
  String get graphhopperServerReachable =>
      'Is-server GraphHopper huwa aċċessibbli';

  @override
  String get errorTitle => 'Żball';

  @override
  String get close => 'Agħlaq';

  @override
  String get infoVersion => 'Verżjoni';

  @override
  String get infoProtocol => 'Protokoll';

  @override
  String get infoMaps => 'Mapep';

  @override
  String get infoRouting => 'Routing';

  @override
  String get infoSource => 'Sors';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (self-hosted)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Mhux konness';

  @override
  String get loginWithNostrTitle => 'IDĦOL BL-NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Iċ-ċavetta privata qatt ma titlaq mill-apparat tiegħek · Rakkomandat';

  @override
  String get nsecLoginOption => 'Daħħal nsec';

  @override
  String get nsecLoginDescription =>
      'Iċ-ċavetta privata maħżuna lokalment · Inqas sigur';

  @override
  String get connectedViaAmber => 'Konness permezz ta\' Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Konness permezz ta\' nsec';

  @override
  String get publicKeyLabel => 'ĊAVETTA PUBBLIKA';

  @override
  String get npubCopiedToClipboard => 'npub ikkupjat fil-clipboard';

  @override
  String get logoutTitle => 'Aqta\' l-konnessjoni';

  @override
  String get logoutConfirmation =>
      'Tħassar il-kredenzjali Nostr minn dan l-apparat?';

  @override
  String get logoutButton => 'Aqta\' l-konnessjoni';

  @override
  String get nostrIdentityInfo =>
      'B\'identità Nostr tista\' tippubblika twissijiet tat-traffiku, rapporti u punti ta\' interess b\'mod deċentralizzat fuq in-netwerk Nostr, mingħajr servers ċentrali.';

  @override
  String get warningTitle => 'Twissija';

  @override
  String get nsecWarning =>
      'Inti wasal biex tixħet iċ-ċavetta privata Nostr tiegħek direttament fl-app mingħajr protezzjoni. Kwalunkwe persuna b\'aċċess fiżiku jew remot għall-apparat tiegħek tista\' taqraha u tieħu kontroll permanenti tal-identità Nostr tiegħek.';

  @override
  String get amberSecureMethodHint =>
      '✦  Il-metodu sikur huwa Amber (NIP-55): l-nsec qatt ma jħalli l-vault tal-firma tal-app.';

  @override
  String get nsecRiskAcknowledgment => 'Nifhem ir-riskju u xtieq inkompli';

  @override
  String get continueButton => 'Kompli';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber huwa firmatarju ta\' app Android konformi ma\' NIP-55. Iċ-ċavetta privata tiegħek tibqa\' iżolata ġewwa Amber u qatt ma tinqasam.';

  @override
  String get requestKeyFromAmber => 'Itlob iċ-ċavetta pubblika minn Amber';

  @override
  String get amberNotFound =>
      'Amber ma nstabx. Installa mill-Play Store jew daħħal in-npub tiegħek manwalment.';

  @override
  String get waitingForAmberResponse => 'Qed nistenna r-risposta ta\' Amber…';

  @override
  String get pasteNpubManually => 'Ippejstja n-npub tiegħek manwalment:';

  @override
  String get confirmNpub => 'Ikkonferma n-npub';

  @override
  String get enterNsecTitle => 'Daħħal nsec';

  @override
  String get loginButton => 'Idħol';

  @override
  String get invalidNpubTitle => 'npub invalidu';

  @override
  String get invalidNsecTitle => 'nsec invalidu';

  @override
  String get invalidNpubMessage => 'Kun ċert li ppejstjajt l-npub korrett.';

  @override
  String get invalidNsecMessage => 'Kun ċert li ppejstjajt l-nsec korrett.';

  @override
  String get amberResponseError => 'Żball fir-risposta ta\' Amber';

  @override
  String get ok => 'OK';

  @override
  String get or => 'jew';

  @override
  String get gpsNotActiveTitle => 'GPS mhux attiv';

  @override
  String get gpsDisabledMessage =>
      'Attiva l-GPS fis-settings tal-apparat tiegħek biex tikseb il-pożizzjoni tiegħek u tuża n-navigazzjoni.';

  @override
  String get openSettings => 'Settings';

  @override
  String get myLocation => 'Il-pożizzjoni tiegħi';

  @override
  String get loginToReport =>
      'Idħol bl-Nostr (sezzjoni Profil) biex tirrapporta avvenimenti';

  @override
  String get navigateHere => 'Naviga hawn';

  @override
  String get reportEventHere => 'Irrapporta avveniment hawn';

  @override
  String get zapSendSats => 'Zap ⚡ (ibgħat sats)';

  @override
  String get sendZap => 'Ibgħat Zap';

  @override
  String get chooseAmountSats => 'Agħżel l-ammont f\'satoshi (sats):';

  @override
  String get customAmount => 'Ammont personalizzat…';

  @override
  String get zapSending => 'Qed jintbagħat…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Qed nikseb l-indirizz Lightning…';

  @override
  String get noLightningAddress =>
      'Dan ir-rapporteur m\'għandu l-ebda indirizz Lightning';

  @override
  String get requestingInvoice => 'Qed nitlob fattura…';

  @override
  String get lnurlUnavailable => 'LNURL mhux disponibbli';

  @override
  String get invoiceFailed => 'Ma jistax jiġi ġenerat fattura';

  @override
  String get openingWallet => 'Qed niftaħ il-wallet…';

  @override
  String get payingViaNwc => 'Qed inħallas permezz ta\' NWC…';

  @override
  String get noLightningWallet => 'L-ebda wallet Lightning ma nstabet';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats mibgħuta!';
  }

  @override
  String get stillThere => 'Għadha hemm';

  @override
  String get notThereAnymore => 'M\'għadhiex hemm';

  @override
  String get loginToConfirm =>
      'Idħol bl-Nostr biex tikkonferma jew tikkontesta';

  @override
  String get reportAnEvent => 'Irrapporta avveniment';

  @override
  String get optionalComment => 'Kumment fakultattiv…';

  @override
  String get publish => 'Ippubblika';

  @override
  String get publishing => 'Qed jippubblika…';

  @override
  String get reportPublished => 'Rapport ippubblikat ✓';

  @override
  String get myReports => 'IR-RAPPORTI TIEGĦI';

  @override
  String get noReportsYet => 'L-ebda rapport ippubblikat';

  @override
  String get zapBalance => 'Bilanċ Zap';

  @override
  String get satoshiFromReports => 'Satoshi riċevuti mir-rapporti tiegħek';

  @override
  String get reputationHigh => 'Għoli';

  @override
  String get reputationMedium => 'Medju';

  @override
  String get reputationLow => 'Baxx';

  @override
  String reputationLabel(String level) {
    return 'Reputazzjoni $level';
  }

  @override
  String reliability(int pct) {
    return 'Affidabbiltà: $pct%';
  }

  @override
  String get confirmedLabel => 'Ikkonfermat';

  @override
  String get removedLabel => 'Imneħħi';

  @override
  String get positionLabel => 'Pożizzjoni';

  @override
  String get loadingLabel => 'Qed jitgħabba…';

  @override
  String get sectionWebSearch => 'Tfittxija fuq il-web';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Ippejstja l-NWC URI tiegħek (Alby Hub, Mutiny, Cashu…) biex tħallas Zaps direttament mill-app.';

  @override
  String get searchEngineQwantDesc =>
      'Ewropea, privatezza l-ewwel. L-ebda traċċar, l-ebda profili ta\' reklam. Rakkomandat.';

  @override
  String get searchEngineBraveDesc =>
      'Indiċi indipendenti, sors miftuħ. L-ebda dipendenza fuq Google jew Bing. Żero profiling.';

  @override
  String get searchEngineDdgDesc =>
      'Ffukat fuq il-privatezza u popolari. Ir-riżultati parzjalment minn Bing — żomm dan f\'moħħok.';

  @override
  String get searchEngineStartpageDesc =>
      'Riżultati tal-Google mingħajr ma tagħti d-data tiegħek lil Google. Kompromess raġonevoli.';

  @override
  String get searchEngineGoogleDesc =>
      'Effettiv ħafna. Imma ftakar: Google jafek aħjar minn ommok u jbigħ kollox lill-pubbliċitarji. L-għażla tiegħek, ovvjament. 🍪';

  @override
  String get categoryPolice => 'Pulizija';

  @override
  String get categorySpeedCamera => 'Kamera tal-veloċità';

  @override
  String get categoryTrafficJam => 'Ingorġ tat-traffiku';

  @override
  String get categoryAccident => 'Inċident';

  @override
  String get categoryRoadClosure => 'Għeluq tat-triq';

  @override
  String get categoryConstruction => 'Kostruzzjoni';

  @override
  String get categoryHazard => 'Periklu';

  @override
  String get categoryRoadCondition => 'Kundizzjoni tat-triq';

  @override
  String get categoryPothole => 'Toqba fit-triq';

  @override
  String get categoryFog => 'Ċpar';

  @override
  String get categoryIce => 'Silġ';

  @override
  String get categoryAnimal => 'Annimal';

  @override
  String get categoryOther => 'Oħra';

  @override
  String get dateTimeLabel => 'Data / ħin';

  @override
  String minutesAgo(int count) {
    return '$count min ilu';
  }

  @override
  String hoursAgo(int count) {
    return '${count}s ilu';
  }

  @override
  String daysAgo(int count) {
    return '${count}j ilu';
  }

  @override
  String get sectionFavorites => 'Postijiet miftuħin';

  @override
  String get addFavorite => 'Żid post';

  @override
  String get favoriteLabelHint => 'Isem (eż. Dar, Uffiċċju)';

  @override
  String get favoriteAddressHint => 'Indirizz';

  @override
  String get favoriteGeocodingError =>
      'Indirizz ma nstabx. Ipprova indirizz aktar speċifiku.';

  @override
  String get trafficAlertTitle => 'Traffiku ġdid fuq ir-rotta';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category irrappurtat $age fuq ir-rotta tiegħek.\n\nTrid issib rotta alternattiva?';
  }

  @override
  String get trafficContinue => 'Kompli';

  @override
  String get trafficRecalculate => 'Erġa\' kalkola r-rotta';

  @override
  String get navExitTitle => 'Oħroġ min-navigazzjoni?';

  @override
  String get navExitBody =>
      'Trid twaqqaf in-navigazzjoni u tirritorna fil-mappa?';

  @override
  String get navContinue => 'Kompli n-navigazzjoni';

  @override
  String get navExit => 'Iva, oħroġ';

  @override
  String get loadingInfo => 'Qiegħed itella\' l-informazzjoni…';

  @override
  String get conditionsOnRoute => 'Kundizzjonijiet fuq ir-rotta';

  @override
  String get calculateRoute => 'Ikkalkola r-rotta';

  @override
  String get sectionNavigationVoice => 'Vuċi tan-navigazzjoni';

  @override
  String get voiceGuidance => 'Gwida bil-vuċi';

  @override
  String get voiceGuidanceDesc =>
      'Aqra l-istruzzjonijiet tad-dawra b\'leħen għoli waqt in-navigazzjoni';

  @override
  String get testVoiceEngine => 'Ittestja l-magna tal-vuċi';

  @override
  String get testVoiceEngineDesc =>
      'Mess biex tivverifika l-magna TTS u tikseb istruzzjonijiet ta\' konfigurazzjoni';

  @override
  String get ttsDialogTitle => 'Nieqsa l-magna tal-vuċi';

  @override
  String get ttsDialogBody =>
      'Ma nstabet l-ebda magna Text-to-Speech li taħdem.\n\nRoadstr jiddependi biss fuq software open source — installa waħda minn dawn il-magni b\'xejn minn F-Droid:';

  @override
  String get ttsRhvoiceDesc => 'Vuċi naturali, lista limitata ta\' lingwi';

  @override
  String get ttsEspeakDesc => 'Tkopri aktar minn 100 lingwa, vuċi robotika';

  @override
  String get ttsInstallNote =>
      '⚠️ Wara l-installazzjoni:\n1. Settings tal-Android → Aċċessibbiltà → Test-to-Speech\n2. Agħżel il-magna li għadek kif installajt\n3. Niżżel id-dejta tal-vuċi tal-lingwa tiegħek\n4. Erġa\' ibda Roadstr għal kollox';

  @override
  String get ttsTestNow => 'Ittestja issa';

  @override
  String get voiceUnsupportedTitle => 'Gwida bil-vuċi mhix disponibbli';

  @override
  String get voiceUnsupportedBody =>
      'Il-lingwa tiegħek għadha mhix appoġġjata għal struzzjonijiet ta\' dawra mitkellma. L-istruzzjonijiet tan-navigazzjoni se jibqgħu jidhru bħala test fuq l-iskrin.';

  @override
  String get kokoroModelTitle => 'Mudell tal-vuċi (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Mhux imsejjaħ · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Qed titniżżel...';

  @override
  String get kokoroModelStatusReady => 'Mudell tal-vuċi lest';

  @override
  String get kokoroModelDownloadBtn => 'Niżżel';

  @override
  String get kokoroModelSupportedLangs =>
      'Jappoġġja: Taljan, Ingliż, Spanjol, Franċiż, Ġappuniż, Ċiniż, Portugiż';

  @override
  String get autoDarkMode => 'Tema skura awtomatika';

  @override
  String get autoDarkModeDesc => 'Tattiva t-tema skura fil-punent tax-xemx';

  @override
  String get arrivedTitle => '🎉 Wasalt!';

  @override
  String get arrivedBody => 'Laħaqt id-destinazzjoni tiegħek.';

  @override
  String get arrivedFeedbackPrompt => 'Kif marret?';

  @override
  String get feedbackBad => 'Ħażin';

  @override
  String get feedbackGood => 'Tajjeb!';

  @override
  String get feedbackDialogTitle => 'Għidilna x\'mar ħażin';

  @override
  String get feedbackHint => 'Iddeskrivi l-problema…';

  @override
  String get feedbackSent => 'Feedback intbagħat — grazzi! 🙏';

  @override
  String get feedbackSubmit => 'Ibgħat';

  @override
  String get transportModeCar => 'Karozza';

  @override
  String get transportModeWalk => 'Bil-mixi';

  @override
  String etaArrivalLabel(String time) {
    return 'Wasl. $time';
  }

  @override
  String get supportRoadstr => 'Appoġġja lil Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address ikkupjat fil-clipboard';
  }

  @override
  String get disclaimerTitle => 'Avviż importanti';

  @override
  String get disclaimerAccept => 'Qrajt u naċċetta';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Aqra fuq Wikipedija';

  @override
  String searchOnEngine(String engine) {
    return 'Fittex fuq $engine';
  }

  @override
  String get plannerFromHint => 'Minn…';

  @override
  String get plannerToHint => 'Destinazzjoni…';

  @override
  String departEta(String dep, String arr) {
    return 'Tluq $dep  →  Wasla $arr';
  }

  @override
  String get modeCar => 'Karozza';

  @override
  String get modeBike => 'Rota';

  @override
  String get modeWalk => 'Bil-mixi';

  @override
  String windSpeed(String speed) {
    return 'riħ $speed km/h';
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
  String get weatherClear => 'Ċar';

  @override
  String get weatherPartlyCloudy => 'Parzjalment imkḫajjar';

  @override
  String get weatherCloudy => 'Imkḫajjar';

  @override
  String get weatherFog => 'Ċpar';

  @override
  String get weatherLightRain => 'Xita ħafifa';

  @override
  String get weatherRain => 'Xita';

  @override
  String get weatherSnow => 'Borra';

  @override
  String get weatherShowers => 'Xita qawwija';

  @override
  String get weatherThunderstorm => 'Maltempata';

  @override
  String get ztlAheadWarning =>
      'Żona ZTL quddiem — ir-rotta tidħol f\'żona ristretta';

  @override
  String get ztlInsideWarning => 'Żona ta\' traffiku ristrett';

  @override
  String get onboardingAppSubtitle => 'Navigazzjoni Nostr open-source';

  @override
  String get onboardingWelcomeTitle => 'Merħba';

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
  String get onboardingGetStarted => 'Ibda';

  @override
  String get onboardingNostrTitle => 'Identità Nostr';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Konness';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (rakkomandat)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'Ċavetta nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Ċavetta nsec mhux valida — iċċekkja u erġa\' pprova.';

  @override
  String get onboardingSkip => 'Aqbeż issa';

  @override
  String get onboardingContinue => 'Kompli';

  @override
  String get onboardingEnterNsec => 'Daħħal iċ-ċavetta nsec';

  @override
  String get onboardingSetupTitle => 'Issettja Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Post';

  @override
  String get onboardingLocationGranted => 'Ingħata aċċess għall-post';

  @override
  String get onboardingLocationRequired => 'Meħtieġ għan-navigazzjoni GPS';

  @override
  String get onboardingGrantButton => 'Agħti';

  @override
  String get onboardingGrapheneTitle => 'Utenti GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'Gwida bil-vuċi AI (mhux obbligatorja)';

  @override
  String get onboardingVoiceReady => 'Mudell tal-vuċi Kokoro lest';

  @override
  String get onboardingVoiceDownloading => 'Qed jitniżżel il-mudell tal-vuċi…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Niżżel';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Int lest biex tinnawiga!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Ejja mmorru!';

  @override
  String get onboardingProfileLoading => 'Qed jitgħabba l-profil…';

  @override
  String get onboardingNsecHint => 'nsec1…';
}
