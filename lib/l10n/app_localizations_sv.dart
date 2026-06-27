// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Swedish (`sv`).
class AppLocalizationsSv extends AppLocalizations {
  AppLocalizationsSv([String locale = 'sv']) : super(locale);

  @override
  String get searchHint => 'Vart vill du åka?';

  @override
  String get calculatingRoute => 'Beräknar rutt…';

  @override
  String get routeNotFoundTitle => 'Rutt hittades inte';

  @override
  String get noRouteFound => 'Ingen rutt hittades. Kontrollera din anslutning.';

  @override
  String get emptyServerResponse =>
      'Tomt serversvar. Kontrollera din anslutning.';

  @override
  String routeError(String error) {
    return 'Fel vid ruttberäkning: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS ej tillgänglig — Inställningar → App → Roadstr → Behörigheter → Plats';

  @override
  String get acquiringGps => 'Hämtar GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopper-server ej konfigurerad — använder OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper API-nyckel ej konfigurerad — använder OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService API-nyckel ej konfigurerad — använder OSRM';

  @override
  String get chooseRoute => 'Välj rutt';

  @override
  String routeOptionsCount(int count) {
    return '$count alternativ';
  }

  @override
  String get cancel => 'Avbryt';

  @override
  String get startNavigation => 'Starta navigering';

  @override
  String get fastestRoute => 'Snabbaste';

  @override
  String get now => 'Nu';

  @override
  String get history => 'Historik';

  @override
  String get clearHistory => 'Rensa';

  @override
  String get selectedPosition => 'Vald position';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Meny';

  @override
  String get settingsTitle => 'Inställningar';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Karta';

  @override
  String get sectionPrivacy => 'Sekretess';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Språk';

  @override
  String get themeLightNostr => 'Ljust · Nostr Violett';

  @override
  String get themeLightBitcoin => 'Ljust · Bitcoin Orange';

  @override
  String get langSystem => 'Systemstandard';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Håll skärmen på';

  @override
  String get keepScreenOnDescription => 'Förhindrar viloläge under navigering';

  @override
  String get rotateMap => 'Kartan följer riktningen';

  @override
  String get rotateMapDescription => 'Kartan roterar baserat på körriktning';

  @override
  String get mapTileUrlLabel => 'URL för kartbrickor';

  @override
  String get routingProviderLabel => 'Ruttleverantör';

  @override
  String get osrmProvider => 'OSRM (offentlig, ingen nyckel krävs)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokal/privat)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API-nyckel)';

  @override
  String get openrouteProvider => 'OpenRouteService (API-nyckel)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API-nyckel (valfritt)';

  @override
  String get verify => 'Verifiera';

  @override
  String get graphhopperServerUrlRequired =>
      'Ange server-URL innan verifiering.';

  @override
  String get successTitle => 'Framgång';

  @override
  String get graphhopperServerReachable => 'GraphHopper-server nåbar';

  @override
  String get errorTitle => 'Fel';

  @override
  String get close => 'Stäng';

  @override
  String get privacyMode => 'Sekretessläge';

  @override
  String get privacyModeDescription => 'Skicka inte anonym telemetridata';

  @override
  String get infoVersion => 'Version';

  @override
  String get infoProtocol => 'Protokoll';

  @override
  String get infoMaps => 'Kartor';

  @override
  String get infoRouting => 'Ruttning';

  @override
  String get infoSource => 'Källa';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (självhostad)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (moln)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Ej ansluten';

  @override
  String get loginWithNostrTitle => 'LOGGA IN MED NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Privat nyckel lämnar aldrig din enhet · Rekommenderas';

  @override
  String get nsecLoginOption => 'Infoga nsec';

  @override
  String get nsecLoginDescription =>
      'Privat nyckel lagras lokalt · Mindre säkert';

  @override
  String get connectedViaAmber => 'Ansluten via Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Ansluten via nsec';

  @override
  String get publicKeyLabel => 'OFFENTLIG NYCKEL';

  @override
  String get npubCopiedToClipboard => 'npub kopierad till urklipp';

  @override
  String get logoutTitle => 'Koppla från';

  @override
  String get logoutConfirmation =>
      'Ta bort Nostr-uppgifter från den här enheten?';

  @override
  String get logoutButton => 'Koppla från';

  @override
  String get nostrIdentityInfo =>
      'Med en Nostr-identitet kan du publicera trafikvarningar, rapporter och intressepunkter på ett decentraliserat sätt i Nostr-nätverket, utan centrala servrar.';

  @override
  String get warningTitle => 'Varning';

  @override
  String get nsecWarning =>
      'Du håller på att skriva in din Nostr-privata nyckel direkt i en app. Alla med fysisk eller fjärråtkomst till din enhet kan läsa den och permanent ta kontroll över din Nostr-identitet.';

  @override
  String get amberSecureMethodHint =>
      '✦  Den säkra metoden är Amber (NIP-55): nsec lämnar aldrig app-signerarvalvet.';

  @override
  String get nsecRiskAcknowledgment =>
      'Jag förstår risken och vill ändå fortsätta';

  @override
  String get continueButton => 'Fortsätt';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber är en NIP-55-kompatibel Android-appsignerare. Din privata nyckel förblir isolerad i Amber och delas aldrig.';

  @override
  String get requestKeyFromAmber => 'Begär offentlig nyckel från Amber';

  @override
  String get amberNotFound =>
      'Amber hittades inte. Installera det från Play Store eller ange din npub manuellt.';

  @override
  String get waitingForAmberResponse => 'Väntar på Amber-svar…';

  @override
  String get pasteNpubManually => 'Klistra in din npub manuellt:';

  @override
  String get confirmNpub => 'Bekräfta npub';

  @override
  String get enterNsecTitle => 'Infoga nsec';

  @override
  String get loginButton => 'Logga in';

  @override
  String get invalidNpubTitle => 'Ogiltig npub';

  @override
  String get invalidNsecTitle => 'Ogiltig nsec';

  @override
  String get invalidNpubMessage => 'Kontrollera att du klistrade in rätt npub.';

  @override
  String get invalidNsecMessage => 'Kontrollera att du klistrade in rätt nsec.';

  @override
  String get amberResponseError => 'Amber-svarsfel';

  @override
  String get ok => 'OK';

  @override
  String get or => 'eller';

  @override
  String get gpsNotActiveTitle => 'GPS ej aktiv';

  @override
  String get gpsDisabledMessage =>
      'Aktivera GPS i enhetsinställningarna för att få din plats och använda navigering.';

  @override
  String get openSettings => 'Inställningar';

  @override
  String get myLocation => 'Min plats';

  @override
  String get loginToReport =>
      'Logga in med Nostr (Profil-sektion) för att rapportera händelser';

  @override
  String get navigateHere => 'Navigera hit';

  @override
  String get reportEventHere => 'Rapportera händelse här';

  @override
  String get zapSendSats => 'Zap ⚡ (skicka sats)';

  @override
  String get sendZap => 'Skicka en Zap';

  @override
  String get chooseAmountSats => 'Välj belopp i satoshi (sats):';

  @override
  String get customAmount => 'Anpassat belopp…';

  @override
  String get zapSending => 'Skickar…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Hämtar Lightning-adress…';

  @override
  String get noLightningAddress =>
      'Den här rapportören har ingen Lightning-adress';

  @override
  String get requestingInvoice => 'Begär faktura…';

  @override
  String get lnurlUnavailable => 'LNURL ej tillgänglig';

  @override
  String get invoiceFailed => 'Kan inte generera faktura';

  @override
  String get openingWallet => 'Öppnar plånbok…';

  @override
  String get payingViaNwc => 'Betalar via NWC…';

  @override
  String get noLightningWallet => 'Ingen Lightning-plånbok hittades';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats skickade!';
  }

  @override
  String get stillThere => 'Finns fortfarande';

  @override
  String get notThereAnymore => 'Finns inte längre';

  @override
  String get loginToConfirm =>
      'Logga in med Nostr för att bekräfta eller bestrida';

  @override
  String get reportAnEvent => 'Rapportera en händelse';

  @override
  String get optionalComment => 'Valfri kommentar…';

  @override
  String get publish => 'Publicera';

  @override
  String get publishing => 'Publicerar…';

  @override
  String get reportPublished => 'Rapport publicerad ✓';

  @override
  String get myReports => 'MINA RAPPORTER';

  @override
  String get noReportsYet => 'Inga rapporter publicerade';

  @override
  String get zapBalance => 'Zap-saldo';

  @override
  String get satoshiFromReports => 'Satoshi mottagna från dina rapporter';

  @override
  String get reputationHigh => 'Hög';

  @override
  String get reputationMedium => 'Medel';

  @override
  String get reputationLow => 'Låg';

  @override
  String reputationLabel(String level) {
    return 'Rykte $level';
  }

  @override
  String reliability(int pct) {
    return 'Tillförlitlighet: $pct%';
  }

  @override
  String get confirmedLabel => 'Bekräftad';

  @override
  String get removedLabel => 'Borttagen';

  @override
  String get positionLabel => 'Position';

  @override
  String get loadingLabel => 'Laddar…';

  @override
  String get sectionWebSearch => 'Webbsökning';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Klistra in din NWC-URI (Alby Hub, Mutiny, Cashu…) för att betala Zaps direkt från appen.';

  @override
  String get searchEngineQwantDesc =>
      'Europeisk, integritetsfokuserad. Ingen spårning, inga annonsprofiler. Rekommenderas.';

  @override
  String get searchEngineBraveDesc =>
      'Oberoende index, öppen källkod. Inget Google- eller Bing-beroende. Noll profilering.';

  @override
  String get searchEngineDdgDesc =>
      'Integritetsfokuserad och populär. Resultat delvis från Bing — ha det i åtanke.';

  @override
  String get searchEngineStartpageDesc =>
      'Google-resultat utan att lämna dina data till Google. En rimlig kompromiss.';

  @override
  String get searchEngineGoogleDesc =>
      'Mycket effektivt. Men kom ihåg: Google känner dig bättre än din mamma och säljer allt till annonsörer. Ditt val. 🍪';

  @override
  String get categoryPolice => 'Polis';

  @override
  String get categorySpeedCamera => 'Fartkamera';

  @override
  String get categoryTrafficJam => 'Trafikstockning';

  @override
  String get categoryAccident => 'Olycka';

  @override
  String get categoryRoadClosure => 'Vägstängning';

  @override
  String get categoryConstruction => 'Vägarbete';

  @override
  String get categoryHazard => 'Fara';

  @override
  String get categoryRoadCondition => 'Väglag';

  @override
  String get categoryPothole => 'Väggropp';

  @override
  String get categoryFog => 'Dimma';

  @override
  String get categoryIce => 'Is';

  @override
  String get categoryAnimal => 'Djur';

  @override
  String get categoryOther => 'Övrigt';

  @override
  String get dateTimeLabel => 'Datum / tid';

  @override
  String minutesAgo(int count) {
    return 'för $count min sedan';
  }

  @override
  String hoursAgo(int count) {
    return 'för ${count}h sedan';
  }

  @override
  String daysAgo(int count) {
    return 'för ${count}d sedan';
  }
}
