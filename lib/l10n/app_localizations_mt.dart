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
  String get sectionPrivacy => 'Privatezza';

  @override
  String get sectionInfo => 'Informazzjoni';

  @override
  String get sectionLanguage => 'Lingwa';

  @override
  String get themeLightNostr => 'Ċar · Nostr Vjola';

  @override
  String get themeLightBitcoin => 'Ċar · Bitcoin Oranġjo';

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
  String get privacyMode => 'Modalità tal-privatezza';

  @override
  String get privacyModeDescription => 'Tibgħatx data telemetrija anonima';

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
}
