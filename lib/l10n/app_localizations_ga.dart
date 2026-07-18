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
  String get autoCenterOnLaunch => 'Láraigh ar mo shuíomh ag an tosú';

  @override
  String get autoCenterOnLaunchDesc =>
      'Ní úsáideann sé GPS go huathoibríoch ach amháin má deonaíodh cead suímh cheana';

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
      'Is aip loingseoireachta thurgnamhach, foinse oscailte, arna cothabháil ag an bpobal í Roadstr atá bunaithe ar shonraí OpenStreetMap agus ar phrótacal Nostr, agus atá ar fáil lena húsáid in aon tír. Trí an aip seo a íoslódáil, a shuiteáil nó a úsáid, glacann an t-úsáideoir go neamhchoinníollach le gach ceann de na téarmaí seo a leanas, gan aon teorannú críche.\n\n🚗 SÁBHÁILTEACHT AR BHÓTHAR AR DTÚS\nNí mór don tiománaí a shúile a choinneáil ar an mbóthar i gcónaí agus cloí le gach dlí tráchta is infheidhme agus le comharthaíocht atá curtha suas, a mbeidh tosaíocht acu i gcónaí ar aon treoir ón aip. Ná hoibrigh an gléas riamh agus tú ag tiomáint; daingnigh é i mbuanáit fhormheasta atá le feiceáil sula gcuirfidh tú chun bóthair, agus ná díraigh d\'aird ón mbóthar riamh chun idirghníomhú leis fad is atá an fheithicil ag gluaiseacht.\n\n⚠️ GLACADH LEIS AN mBAOL — AR FUD AN DOMHAIN\nTrí Roadstr a úsáid, in aon tír agus faoi aon chóras dlí, glacann an t-úsáideoir go feasach agus go deonach le GACH baol a bhaineann lena húsáid, lena n-áirítear, gan a bheith teoranta dóibh: taismí tráchta, díobháil phearsanta, bás, damáiste maoine, damáiste feithicle, fíneálacha, pionóis riaracháin, tarraingt, coigistiú feithicle, dliteanas coiriúil, nó aon iarmhairt eile a eascraíonn go díreach nó go hindíreach as brath ar an aip. Is ar an úsáideoir amháin atá an fhreagracht iomlán as gach cinneadh tiomána agus loingseoireachta.\n\n🚫 GAN BHARÁNTA\nCuirtear Roadstr ar fáil go docht mar atá sé (\"AS IS\") agus mar atá sé ar fáil (\"AS AVAILABLE\"), gan aon bharánta d\'aon chineál, cibé acu sainráite, intuigthe nó reachtúil — lena n-áirítear, gan teorannú, barántaí cruinnis, iomláine, iontaofachta, infhaighteachta, indíolachta, oiriúnachta chun críche ar leith, agus neamhshárú. Tagann sonraí léarscáile, bealacharú, teorainneacha luais, ceamaraí luais, agus faisnéis maidir le criosanna trácht theoranta (ZTL/ZAC/LTZ) ó fhoinsí oscailte arna gcothabháil ag an bpobal (OpenStreetMap, Overpass API), a d\'fhéadfadh a bheith neamhiomlán, as dáta, nó mícheart d\'aon tír, réigiún nó bardas, ag am ar bith agus gan fógra. Is ar an úsáideoir amháin atá an fhreagracht as a fhíorú go neamhspleách, roimh an turas agus lena linn, an bhfuil aon bhealach a mholtar dleathach agus inrochtana de réir na comharthaíochta agus na rialachán áitiúla oifigiúla.\n\n📍 CRUINNEAS AGUS GPS\nD\'fhéadfadh suíomh GPS a bheith mícheart nó gan a bheith ar fáil. Ní sholáthraítear na treoracha, na faid agus na foláirimh go léir ach mar threoir amháin agus níor cheart brath orthu riamh mar an t-aon bhunús le cinneadh tiomána.\n\n🛡️ TEORANNÚ DLITEANAIS\nA mhéid is mó a cheadaítear faoin dlí is infheidhme in aon dlínse, ní bheidh na forbróirí, na rannpháirtithe, ná aon pháirtí a bhí bainteach le Roadstr a chruthú nó a dháileadh faoi dhliteanas as aon damáistí díreacha, indíreacha, teagmhasacha, iarmhartacha, speisialta, samplacha nó pionósacha d\'aon chineál — lena n-áirítear díobháil phearsanta, bás, nó caillteanas airgeadais — a eascraíonn as úsáid nó éagumas úsáide na haipe nó a bhaineann léi, fiú má cuireadh in iúl dóibh go bhféadfadh damáistí den sórt sin tarlú. I gcás nach gceadaíonn dlínse ar leith cuid nó iomlán an teorannaithe seo, teorannófar an dliteanas a mhéid is lú a cheadaítear faoi dhlí na dlínse sin.\n\n🤝 SLÁNAÍOCHT\nAontaíonn an t-úsáideoir na forbróirí agus na rannpháirtithe a shlánú agus a choinneáil saor ó dhíobháil in aghaidh aon éilimh, damáiste, caillteanais nó costais (lena n-áirítear táillí dlí) a eascraíonn as úsáid na haipe ag an úsáideoir nó as sárú na dtéarmaí seo.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ INSCARTHACHT\nMá mheastar go bhfuil aon fhoráil de na téarmaí seo dofhorghníomhaithe i ndlínse ar leith, teorannófar nó scarfar an fhoráil sin a mhéid is gá amháin, agus fanfaidh na forálacha eile go léir i lánfheidhm.\n\nTrí Roadstr a úsáid, in áit ar bith ar domhan, dearbhaíonn an t-úsáideoir gur léigh, gur thuig, agus gur ghlac sé/sí go neamhchoinníollach le gach téarma thuas, agus glacann sé/sí leis an bhfreagracht iomlán agus chríochnúil — agus an baol go léir — as úsáid na haipe agus as aon iarmhairt a eascraíonn aisti.';

  @override
  String get readOnWikipedia => 'Léigh ar Vicipéid';

  @override
  String get openInBrowser => 'Oscail sa bhrabhsálaí';

  @override
  String get wikipediaLoadFailed => 'Níorbh fhéidir Vicipéid a luchtú go slán.';

  @override
  String get retry => 'Bain triail eile as';

  @override
  String get poiDetailsFromOsm => 'Faisnéis ó OpenStreetMap';

  @override
  String get poiCategory => 'Catagóir';

  @override
  String get poiOperator => 'Oibreoir';

  @override
  String get poiCuisine => 'Ealaín';

  @override
  String get poiAccessibility => 'Inrochtaineacht';

  @override
  String get poiWheelchairYes => 'Inrochtana do chathaoireacha rothaí';

  @override
  String get poiWheelchairLimited =>
      'Rochtain theoranta do chathaoireacha rothaí';

  @override
  String get poiWheelchairNo => 'Gan rochtain do chathaoireacha rothaí';

  @override
  String get poiContact => 'Teagmháil';

  @override
  String get poiAddress => 'Seoladh';

  @override
  String get poiWebsite => 'Suíomh gréasáin';

  @override
  String get poiAccessPrivate => 'Rochtain phríobháideach';

  @override
  String get poiAccessCustomers => 'Custaiméirí amháin';

  @override
  String get poiAccessPermit => 'Cead ag teastáil';

  @override
  String get poiAccessNo => 'Gan rochtain phoiblí';

  @override
  String get poiAccessDestination => 'Rochtain chuig an gceann scríbe amháin';

  @override
  String get poiLightningAccepted => 'Glactar le Lightning';

  @override
  String get poiBitcoinAccepted => 'Glactar le Bitcoin';

  @override
  String get poiParkingDetails => 'Páirceáil';

  @override
  String get poiParkingSurface => 'Ar an dromchla';

  @override
  String get poiParkingUnderground => 'Faoi thalamh';

  @override
  String get poiParkingMultiStorey => 'Ilstórach';

  @override
  String get poiParkingStreetSide => 'Cois sráide';

  @override
  String get poiParkingLane => 'Ar an tsráid';

  @override
  String get poiParkingRooftop => 'Ar an díon';

  @override
  String get poiFee => 'Táille';

  @override
  String get poiFree => 'Saor in aisce';

  @override
  String get poiPaid => 'Íoctha';

  @override
  String get poiCapacity => 'Toilleadh';

  @override
  String get poiMaxStay => 'Uastréimhse fanachta';

  @override
  String get poiPrice => 'Praghas';

  @override
  String get poiChargingDetails => 'Luchtú';

  @override
  String get poiConnectorType2 => 'Cineál 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Díosal';

  @override
  String get poiPetrol95 => 'Peitreal 95';

  @override
  String get poiSmokingAllowed => 'Caitheamh tobac ceadaithe';

  @override
  String get poiSmokingOutside => 'Caitheamh tobac lasmuigh';

  @override
  String get poiSmokingAreas => 'Limistéir chaite tobac';

  @override
  String get poiSmokeFree => 'Saor ó thobac';

  @override
  String get poiOutdoorSeating => 'Suíocháin lasmuigh';

  @override
  String get poiTakeaway => 'Beir leat';

  @override
  String get poiTakeawayOnly => 'Beir leat amháin';

  @override
  String get gpsSignalLost => 'Cailleadh comhartha GPS';

  @override
  String get poiOpenNow => 'Ar oscailt anois';

  @override
  String get poiClosedNow => 'Dúnta';

  @override
  String poiOpensAt(String when) {
    return 'Osclaíonn: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Dúnann: $when';
  }

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
      'Tá crios tráchta srianta chun tosaigh — téann an bealach tríd';

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
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

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
  String get kokoroVoiceGenderTitle => 'Guth';

  @override
  String get kokoroVoiceFemale => 'Baineann';

  @override
  String get kokoroVoiceMale => 'Fireann';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Níl guth fireann ar fáil don teanga seo';

  @override
  String get kokoroSpeedTitle => 'Luas cainte';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Roghnach: is féidir do cheanáin shábháilte a shioncronú idir do ghléasanna trí na hathsheachadáin Nostr, criptithe ó cheann go ceann (NIP-44) le d’eochair féin — ní fheiceann na hathsheachadáin ach téacs criptithe, agus ní féidir le duine ar bith seachas tú féin an t-ábhar a léamh. Cumasaigh am ar bith ó na Socruithe.';

  @override
  String get parkingSaveHere => 'Sábháil páirceáil anseo';

  @override
  String get parkingMarkerTitle => 'Spás páirceála';

  @override
  String get parkingNavigateHere => 'Nascleanúint chuig an bpáirceáil';

  @override
  String get parkingRemove => 'Bain an pháirceáil';

  @override
  String get parkingSavedSnack => 'Spás páirceála sábháilte';

  @override
  String get parkingRemovedSnack => 'Spás páirceála bainte';

  @override
  String get exportFavoritesTitle => 'Easpórtáil ceanáin';

  @override
  String get exportFavoritesDesc =>
      'Sábháil do láithreacha is fearr leat i gcomhad JSON is féidir leat a chúltacú nó a aistriú go gléas eile.';

  @override
  String get exportEncryptToggle => 'Criptigh le pasfhocal';

  @override
  String get exportPasswordHint => 'Pasfhocal';

  @override
  String get exportButton => 'Easpórtáil';

  @override
  String get exportSuccessSnack => 'Ceanáin easpórtáilte';

  @override
  String get exportFailedSnack => 'Theip ar an easpórtáil';

  @override
  String get importFavoritesTitle => 'Iompórtáil ceanáin';

  @override
  String get importPasswordPrompt =>
      'Tá an comhad seo criptithe — cuir isteach an pasfhocal';

  @override
  String importSuccessSnack(int n) {
    return '$n gceanán iompórtáilte';
  }

  @override
  String get importFailedSnack =>
      'Theip ar an iompórtáil — seiceáil an comhad agus an pasfhocal';

  @override
  String get syncFavoritesTitle => 'Sioncronaigh ceanáin';

  @override
  String get syncFavoritesDesc =>
      'Foilsigh do cheanáin, criptithe ó cheann go ceann, chuig d’athsheachadáin Nostr ionas go leanfaidh siad thú ar fud do ghléasanna. Ní fheiceann na hathsheachadáin ach téacs criptithe — ní féidir le duine ar bith seachas tú féin an t-ábhar a léamh.';

  @override
  String get syncNowButton => 'Seol chuig Nostr';

  @override
  String get syncPullButton => 'Faigh ó Nostr';

  @override
  String get syncPassphraseTitle => 'Pasfhrása sioncronaithe (roghnach)';

  @override
  String get syncPassphraseDesc =>
      'Dara sraith criptithe do na ceanáin shioncronaithe: fiú dá gcuirfí d\'eochair Nostr i mbaol, fanann an roinneog ar na hathsheachadáin doléite gan an pasfhrása seo. Cuirfidh tú isteach é uair amháin ar gach gléas nua. Fág folamh chun é a dhíchumasú.';

  @override
  String get syncSuccessSnack => 'Ceanáin sioncronaithe';

  @override
  String get syncFailedSnack => 'Theip ar an sioncronú';

  @override
  String syncLastSyncLabel(String when) {
    return 'Sioncronaithe go deireanach: $when';
  }

  @override
  String get syncNoIdentity =>
      'Logáil isteach le Nostr (Socruithe → Próifíl) chun sioncronú a chumasú';

  @override
  String get onboardingVpnNotice =>
      'Ar mhaithe le príobháideachas uasta — scaiptear tuairiscí ar líonra Nostr — moltar VPN a úsáid. Is é Mullvad, is féidir a íoc le Bitcoin, an rogha mholta.';

  @override
  String get trafficNormal => 'Gnáth-thrácht';

  @override
  String get trafficModerate => 'Trácht measartha';

  @override
  String get trafficHeavy => 'Trácht trom';

  @override
  String get avoidHighwaysChip => 'Seachain mótarbhealaí';

  @override
  String get avoidTollsChip => 'Seachain dolaí';

  @override
  String get preferShorterChip => 'An bealach is giorra';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Taispeáin réamhamharc an bhealaigh';

  @override
  String get avoidHighwaysAndTolls => 'Seachain mótarbhealaí agus dolaí';

  @override
  String get avoidRouteUnavailable =>
      'Níor aimsíodh bealach a sheachnaíonn mótarbhealaí agus dolaí araon.';

  @override
  String get avoidanceUnavoidableSection =>
      'Laghdaíonn mótarbhealaí/dolaí · cuid dosheachanta';
}
