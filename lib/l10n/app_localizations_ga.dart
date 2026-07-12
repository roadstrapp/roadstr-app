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
  String get sectionInfo => 'Eolas';

  @override
  String get sectionLanguage => 'Teanga';

  @override
  String get themeLightNostr => 'Geal · Nostr Corcairghorm';

  @override
  String get themeLightBitcoin => 'Geal · Bitcoin Oráiste';

  @override
  String get themeDarkNostr => 'Dorcha · Nostr Corcairghorm';

  @override
  String get themeDarkBitcoin => 'Dorcha · Bitcoin Oráiste';

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

  @override
  String get trafficAlertTitle => 'Trácht nua ar an mbealach';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category tuairiscithe $age ar do bhealach.\n\nAn dteastaíonn uait bealach malartach a aimsiú?';
  }

  @override
  String get trafficContinue => 'Lean ar aghaidh';

  @override
  String get trafficRecalculate => 'Athríomh an bealach';

  @override
  String get navExitTitle => 'Fág an nascleanúint?';

  @override
  String get navExitBody =>
      'An dteastaíonn uait an nascleanúint a stopadh agus filleadh ar an léarscáil?';

  @override
  String get navContinue => 'Lean ar aghaidh le nascleanúint';

  @override
  String get navExit => 'Sea, fág';

  @override
  String get loadingInfo => 'Ag lódáil faisnéise…';

  @override
  String get conditionsOnRoute => 'Coinníollacha ar an mbealach';

  @override
  String get calculateRoute => 'Ríomh an bealach';

  @override
  String get sectionNavigationVoice => 'Guth nascleanúna';

  @override
  String get voiceGuidance => 'Treoir ghutha';

  @override
  String get voiceGuidanceDesc =>
      'Léigh treoracha casta os ard le linn nascleanúna';

  @override
  String get testVoiceEngine => 'Tástáil inneall gutha';

  @override
  String get testVoiceEngineDesc =>
      'Tapáil chun an t-inneall TTS a sheiceáil agus treoracha socraithe a fháil';

  @override
  String get ttsDialogTitle => 'Inneall gutha ar iarraidh';

  @override
  String get ttsDialogBody =>
      'Níor aimsíodh aon inneall Text-to-Speech atá ag feidhmiú.\n\nNíl Roadstr ag brath ach ar bhogearraí foinse oscailte — suiteáil ceann de na hinnill shaora seo ó F-Droid:';

  @override
  String get ttsRhvoiceDesc => 'Guth nádúrtha, liosta teangacha teoranta';

  @override
  String get ttsEspeakDesc => 'Clúdaíonn os cionn 100 teanga, guth róbatach';

  @override
  String get ttsInstallNote =>
      '⚠️ Tar éis na suiteála:\n1. Socruithe Android → Inrochtaineacht → Téacs go Guth\n2. Roghnaigh an t-inneall a shuiteáil tú anois beag\n3. Íoslódáil sonraí gutha do theanga\n4. Atosaigh Roadstr go hiomlán';

  @override
  String get ttsTestNow => 'Tástáil anois';

  @override
  String get voiceUnsupportedTitle => 'Treoir ghutha níl ar fáil';

  @override
  String get voiceUnsupportedBody =>
      'Níl do theanga tacaithe go fóill le haghaidh treoracha cas labhartha. Beidh treoracha nascleanúna le feiceáil fós mar théacs ar an scáileán.';

  @override
  String get kokoroModelTitle => 'Samhail ghutha (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Gan íoslódáil · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Ag íoslódáil...';

  @override
  String get kokoroModelStatusReady => 'Samhail ghutha réidh';

  @override
  String get kokoroModelDownloadBtn => 'Íoslódáil';

  @override
  String get kokoroModelSupportedLangs =>
      'Tacaíonn: Iodáilis, Béarla, Spáinnis, Fraincis, Seapáinis, Sínis, Portaingéilis';

  @override
  String get autoDarkMode => 'Téama dorcha uathoibríoch';

  @override
  String get autoDarkModeDesc => 'Gníomhaíonn an téama dorcha ag luí na gréine';

  @override
  String get settingsImperialUnits => 'Aonaid impiriúla';

  @override
  String get settingsImperialUnitsDesc =>
      'Míle agus troithe in ionad ciliméadair agus méadair';

  @override
  String get arrivedTitle => '🎉 Tháinig tú!';

  @override
  String get arrivedBody => 'Shroich tú do cheann scríbe.';

  @override
  String get arrivedFeedbackPrompt => 'Conas a chuaigh sé?';

  @override
  String get feedbackBad => 'Go dona';

  @override
  String get feedbackGood => 'Go maith!';

  @override
  String get feedbackDialogTitle => 'Inis dúinn cad a chuaigh mícheart';

  @override
  String get feedbackHint => 'Déan cur síos ar an gceist…';

  @override
  String get feedbackSent => 'Aiseolas seolta — go raibh maith agat! 🙏';

  @override
  String get feedbackSubmit => 'Seol';

  @override
  String get transportModeCar => 'Carr';

  @override
  String get transportModeWalk => 'De shiúl';

  @override
  String etaArrivalLabel(String time) {
    return 'Arr. $time';
  }

  @override
  String get supportRoadstr => 'Tacaigh le Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address cóipeáilte';
  }

  @override
  String get disclaimerTitle => 'Fógra tábhachtach';

  @override
  String get disclaimerAccept => 'Léigh mé agus glacaim leis';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Léigh ar Vicipéid';

  @override
  String searchOnEngine(String engine) {
    return 'Cuardaigh ar $engine';
  }

  @override
  String get plannerFromHint => 'Ó…';

  @override
  String get plannerToHint => 'Ceann scríbe…';

  @override
  String departEta(String dep, String arr) {
    return 'Imeacht $dep  →  Sroicheadh $arr';
  }

  @override
  String get modeCar => 'Carr';

  @override
  String get modeBike => 'Rothar';

  @override
  String get modeWalk => 'De chois';

  @override
  String windSpeed(String speed) {
    return 'gaoith $speed km/h';
  }

  @override
  String durationMin(int m) {
    return '$m nóim';
  }

  @override
  String durationHourMin(int h, int m) {
    return '${h}h ${m}nóim';
  }

  @override
  String get weatherClear => 'Geal';

  @override
  String get weatherPartlyCloudy => 'Scamallach go páirteach';

  @override
  String get weatherCloudy => 'Scamallach';

  @override
  String get weatherFog => 'Ceo';

  @override
  String get weatherLightRain => 'Drochshíon';

  @override
  String get weatherRain => 'Báisteach';

  @override
  String get weatherSnow => 'Sneachta';

  @override
  String get weatherShowers => 'Ceathanna';

  @override
  String get weatherThunderstorm => 'Stoirm thoirní';

  @override
  String get ztlAheadWarning =>
      'Crios ZTL chun tosaigh — téann an bealach isteach i gcrios srianta';

  @override
  String get ztlInsideWarning => 'Crios tráchta srianta';

  @override
  String get onboardingAppSubtitle => 'Nascleanúint Nostr foinse oscailte';

  @override
  String get onboardingWelcomeTitle => 'Fáilte';

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
  String get onboardingGetStarted => 'Tosaigh';

  @override
  String get onboardingNostrTitle => 'Aitheantas Nostr';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Ceangailte';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (molta)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'Eochair nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Eochair nsec neamhbhailí — seiceáil agus déan arís.';

  @override
  String get onboardingSkip => 'Scipeáil faoi láthair';

  @override
  String get onboardingContinue => 'Lean ar aghaidh';

  @override
  String get onboardingEnterNsec => 'Cuir isteach an eochair nsec';

  @override
  String get onboardingSetupTitle => 'Roadstr a chumrú';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Suíomh';

  @override
  String get onboardingLocationGranted => 'Deonaíodh rochtain ar shuíomh';

  @override
  String get onboardingLocationRequired => 'Riachtanach do nascleanúint GPS';

  @override
  String get onboardingGrantButton => 'Deontas';

  @override
  String get onboardingGrapheneTitle => 'Úsáideoirí GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'Treoir gutha AI (roghnach)';

  @override
  String get onboardingVoiceReady => 'Múnla gutha Kokoro réidh';

  @override
  String get onboardingVoiceDownloading => 'Ag íoslódáil múnla gutha…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Íoslódáil';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Tá tú réidh le nascleanúint!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Ar aghaidh linn!';

  @override
  String get onboardingProfileLoading => 'Próifíl á luchtú…';

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
      'Ar mhaithe le príobháideachas uasta — scaiptear tuairiscí ar líonra Nostr — moltar VPN a úsáid. Is é Mullvad, is féidir a íoc le Bitcoin, an rogha mholta.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Le haghaidh oibriú iontaofa, socraigh cead suímh na haipe go \"Ceadaigh i gcónaí\", ní hamháin agus an aip in úsáid.';
}
