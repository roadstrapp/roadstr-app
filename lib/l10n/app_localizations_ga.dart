// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Irish (`ga`).
class AppLocalizationsGa extends AppLocalizations {
  AppLocalizationsGa([String locale = 'ga']) : super(locale);

  @override
  String get searchHint => 'Cá háit ar mhaith leat dul?';

  @override
  String get calculatingRoute => 'Ag ríomh bealaigh…';

  @override
  String get routeNotFoundTitle => 'Ní bhfuarthas bealach';

  @override
  String get noRouteFound => 'Ní bhfuarthas bealach. Seiceáil do nasc.';

  @override
  String get emptyServerResponse =>
      'Freagra folamh ón bhfreastalaí. Seiceáil do nasc.';

  @override
  String routeError(String error) {
    return 'Earráid ríomha bealaigh: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS nach bhfuil ar fáil — Settings → App → Roadstr → Permissions → Location';

  @override
  String get acquiringGps => 'Ag fáil GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Freastalaí GraphHopper gan chumrú — ag úsáid OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'Eochair API GraphHopper gan chumrú — ag úsáid OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'Eochair API OpenRouteService gan chumrú — ag úsáid OSRM';

  @override
  String get chooseRoute => 'Roghnaigh bealach';

  @override
  String routeOptionsCount(int count) {
    return '$count rogha';
  }

  @override
  String get cancel => 'Cealaigh';

  @override
  String get startNavigation => 'Tosaigh nascleanúint';

  @override
  String get fastestRoute => 'Is tapúla';

  @override
  String get now => 'Anois';

  @override
  String get history => 'Stair';

  @override
  String get clearHistory => 'Glan';

  @override
  String get selectedPosition => 'Suíomh roghnaithe';

  @override
  String get bottomBarProfile => 'Próifíl';

  @override
  String get bottomBarMenu => 'Roghchlár';

  @override
  String get settingsTitle => 'Socruithe';

  @override
  String get sectionTheme => 'Téama';

  @override
  String get sectionMap => 'Léarscáil';

  @override
  String get sectionPrivacy => 'Príobháideacht';

  @override
  String get sectionInfo => 'Eolas';

  @override
  String get sectionLanguage => 'Teanga';

  @override
  String get themeLightNostr => 'Geal · Nostr Corcairghorm';

  @override
  String get themeLightBitcoin => 'Geal · Bitcoin Oráiste';

  @override
  String get langSystem => 'Réamhshocrú córais';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Coinnigh an scáileán ar siúl';

  @override
  String get keepScreenOnDescription =>
      'Cuireann cosc ar chodladh le linn nascleanúna';

  @override
  String get rotateMap => 'Leanann léarscáil an treo';

  @override
  String get rotateMapDescription =>
      'Rothlaíonn an léarscáil bunaithe ar threo tiomána';

  @override
  String get mapTileUrlLabel => 'URL tile léarscáile';

  @override
  String get routingProviderLabel => 'Soláthraí bealaíochta';

  @override
  String get osrmProvider => 'OSRM (poiblí, gan eochair ag teastáil)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (áitiúil/príobháideach)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (eochair API)';

  @override
  String get openrouteProvider => 'OpenRouteService (eochair API)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'Eochair API GraphHopper (roghnach)';

  @override
  String get verify => 'Deimhnigh';

  @override
  String get graphhopperServerUrlRequired =>
      'Cuir isteach URL an fhreastalaí roimh dheimhniú.';

  @override
  String get successTitle => 'Éirigh leis';

  @override
  String get graphhopperServerReachable =>
      'Tá freastalaí GraphHopper inrochtana';

  @override
  String get errorTitle => 'Earráid';

  @override
  String get close => 'Dún';

  @override
  String get privacyMode => 'Mód príobháideachta';

  @override
  String get privacyModeDescription =>
      'Ná seol sonraí teileamheictrice anaithnid';

  @override
  String get infoVersion => 'Leagan';

  @override
  String get infoProtocol => 'Prótacal';

  @override
  String get infoMaps => 'Léarscáileanna';

  @override
  String get infoRouting => 'Bealaíocht';

  @override
  String get infoSource => 'Foinse';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (féin-óstáilte)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (scamall)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Próifíl';

  @override
  String get notConnected => 'Gan nasc';

  @override
  String get loginWithNostrTitle => 'LOGÁIL ISTEACH LE NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Ní fhágann an eochair phríobháideach do ghléas riamh · Molta';

  @override
  String get nsecLoginOption => 'Cuir isteach nsec';

  @override
  String get nsecLoginDescription =>
      'Eochair phríobháideach stóráilte go háitiúil · Níos lú slándála';

  @override
  String get connectedViaAmber => 'Ceangailte trí Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Ceangailte trí nsec';

  @override
  String get publicKeyLabel => 'EOCHAIR PHOIBLÍ';

  @override
  String get npubCopiedToClipboard => 'npub cóipeáilte go gearrthaisce';

  @override
  String get logoutTitle => 'Dícheangail';

  @override
  String get logoutConfirmation => 'Baineadh dintiúir Nostr den ghléas seo?';

  @override
  String get logoutButton => 'Dícheangail';

  @override
  String get nostrIdentityInfo =>
      'Le céannacht Nostr is féidir leat foláirimh tráchta, tuarascálacha agus pointí spéise a fhoilsiú ar bhealach díláraithe ar líonra Nostr, gan freastalaithe lárnachta.';

  @override
  String get warningTitle => 'Rabhadh';

  @override
  String get nsecWarning =>
      'Tá tú ar tí do eochair phríobháideach Nostr a chaitheamh go díreach isteach in aip gan cosaint ar bith. D\'fhéadfadh duine ar bith a bhfuil rochtain fhisiciúil nó iargúlta aige ar do ghléas é a léamh agus smacht buan a ghlacadh ar d\'aitheantas Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  Is é an modh sábháilte Amber (NIP-55): ní fhágann an nsec bosca taisceáin an sínitheora riamh.';

  @override
  String get nsecRiskAcknowledgment =>
      'Tuigim an riosca agus ba mhaith liom leanúint ar aghaidh';

  @override
  String get continueButton => 'Lean ar aghaidh';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Is sínitheoir aipe Android é Amber atá comhlíontach le NIP-55. Fanann d\'eochair phríobháideach scoite laistigh de Amber agus ní roinntear riamh í.';

  @override
  String get requestKeyFromAmber => 'Iarr eochair phoiblí ó Amber';

  @override
  String get amberNotFound =>
      'Ní bhfuarthas Amber. Suiteáil é ón Play Store nó cuir isteach do npub de láimh.';

  @override
  String get waitingForAmberResponse => 'Ag fanacht le freagra Amber…';

  @override
  String get pasteNpubManually => 'Greamaigh do npub de láimh:';

  @override
  String get confirmNpub => 'Deimhnigh npub';

  @override
  String get enterNsecTitle => 'Cuir isteach nsec';

  @override
  String get loginButton => 'Logáil isteach';

  @override
  String get invalidNpubTitle => 'npub neamhbhailí';

  @override
  String get invalidNsecTitle => 'nsec neamhbhailí';

  @override
  String get invalidNpubMessage => 'Cinntigh gur ghreamaigh tú an npub ceart.';

  @override
  String get invalidNsecMessage => 'Cinntigh gur ghreamaigh tú an nsec ceart.';

  @override
  String get amberResponseError => 'Earráid fhreagra Amber';

  @override
  String get ok => 'OK';

  @override
  String get or => 'nó';

  @override
  String get gpsNotActiveTitle => 'GPS nach bhfuil gníomhach';

  @override
  String get gpsDisabledMessage =>
      'Gníomhachtaigh GPS i socruithe do ghléis chun do shuíomh a fháil agus nascleanúint a úsáid.';

  @override
  String get openSettings => 'Socruithe';

  @override
  String get myLocation => 'Mo shuíomh';

  @override
  String get loginToReport =>
      'Logáil isteach le Nostr (alt Próifíle) chun imeachtaí a thuairisciú';

  @override
  String get navigateHere => 'Nasclean anseo';

  @override
  String get reportEventHere => 'Tuairiscigh imeacht anseo';

  @override
  String get zapSendSats => 'Zap ⚡ (seol sats)';

  @override
  String get sendZap => 'Seol Zap';

  @override
  String get chooseAmountSats => 'Roghnaigh méid i satoshi (sats):';

  @override
  String get customAmount => 'Méid saincheaptha…';

  @override
  String get zapSending => 'Ag seolhadh…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Ag fáil seoladh Lightning…';

  @override
  String get noLightningAddress =>
      'Níl seoladh Lightning ag an tuairisceoir seo';

  @override
  String get requestingInvoice => 'Ag iarraidh sonrasc…';

  @override
  String get lnurlUnavailable => 'LNURL nach bhfuil ar fáil';

  @override
  String get invoiceFailed => 'Ní féidir sonrasc a ghiniúint';

  @override
  String get openingWallet => 'Ag oscailt sparáin…';

  @override
  String get payingViaNwc => 'Ag íoc trí NWC…';

  @override
  String get noLightningWallet => 'Ní bhfuarthas sparán Lightning';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats seolta!';
  }

  @override
  String get stillThere => 'Fós ann';

  @override
  String get notThereAnymore => 'Níl sé ann níos mó';

  @override
  String get loginToConfirm =>
      'Logáil isteach le Nostr chun deimhniú nó díospóid';

  @override
  String get reportAnEvent => 'Tuairiscigh imeacht';

  @override
  String get optionalComment => 'Trácht roghnach…';

  @override
  String get publish => 'Foilsigh';

  @override
  String get publishing => 'Ag foilsiú…';

  @override
  String get reportPublished => 'Tuarascáil foilsithe ✓';

  @override
  String get myReports => 'MO THUARASCÁLACHA';

  @override
  String get noReportsYet => 'Gan tuarascálacha foilsithe';

  @override
  String get zapBalance => 'Iarmhéid Zap';

  @override
  String get satoshiFromReports => 'Satoshi a fuarthas ó do thuarascálacha';

  @override
  String get reputationHigh => 'Ard';

  @override
  String get reputationMedium => 'Meánach';

  @override
  String get reputationLow => 'Íseal';

  @override
  String reputationLabel(String level) {
    return 'Clú $level';
  }

  @override
  String reliability(int pct) {
    return 'Iontaofacht: $pct%';
  }

  @override
  String get confirmedLabel => 'Deimhnithe';

  @override
  String get removedLabel => 'Bainte';

  @override
  String get positionLabel => 'Suíomh';

  @override
  String get loadingLabel => 'Ag luchtú…';

  @override
  String get sectionWebSearch => 'Cuardach gréasáin';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Greamaigh do NWC URI (Alby Hub, Mutiny, Cashu…) chun Zaps a íoc go díreach ón aip.';

  @override
  String get searchEngineQwantDesc =>
      'Eorpach, príobháideacht ar dtús. Gan rianú, gan próifílí fógraíochta. Molta.';

  @override
  String get searchEngineBraveDesc =>
      'Innéacs neamhspleách, foinse oscailte. Gan spleáchas ar Google nó Bing. Nialas próifílithe.';

  @override
  String get searchEngineDdgDesc =>
      'Dírithe ar phríobháideacht agus coitianta. Torthaí go páirteach ó Bing — coinnigh sin i gcuimhne.';

  @override
  String get searchEngineStartpageDesc =>
      'Torthaí Google gan do chuid sonraí a thabhairt do Google. Comhréiteach réasúnach.';

  @override
  String get searchEngineGoogleDesc =>
      'An-éifeachtach. Ach cuimhnigh: tá aithne ag Google ort níos fearr ná do mháthair agus díolann sé gach rud le fógróirí. Do rogha, ar ndóigh. 🍪';

  @override
  String get categoryPolice => 'Póilíní';

  @override
  String get categorySpeedCamera => 'Ceamara luais';

  @override
  String get categoryTrafficJam => 'Plódú tráchta';

  @override
  String get categoryAccident => 'Timpiste';

  @override
  String get categoryRoadClosure => 'Dúnadh bóthair';

  @override
  String get categoryConstruction => 'Tógáil';

  @override
  String get categoryHazard => 'Contúirt';

  @override
  String get categoryRoadCondition => 'Staid bóthair';

  @override
  String get categoryPothole => 'Poll bóthair';

  @override
  String get categoryFog => 'Ceo';

  @override
  String get categoryIce => 'Oighear';

  @override
  String get categoryAnimal => 'Ainmhí';

  @override
  String get categoryOther => 'Eile';

  @override
  String get dateTimeLabel => 'Dáta / am';

  @override
  String minutesAgo(int count) {
    return '$count nóim ó shin';
  }

  @override
  String hoursAgo(int count) {
    return '${count}u ó shin';
  }

  @override
  String daysAgo(int count) {
    return '${count}l ó shin';
  }

  @override
  String get sectionFavorites => 'Áiteanna sábháilte';

  @override
  String get addFavorite => 'Cuir áit leis';

  @override
  String get favoriteLabelHint => 'Ainm (m.sh. Baile, Oifig)';

  @override
  String get favoriteAddressHint => 'Seoladh';

  @override
  String get favoriteGeocodingError =>
      'Seoladh gan aimsiú. Bain triail as seoladh níos sonraí.';
}
