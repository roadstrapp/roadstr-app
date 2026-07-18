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
  String get autoCenterOnLaunch =>
      'Iċċentra fuq il-pożizzjoni tiegħi mat-tħaddim';

  @override
  String get autoCenterOnLaunchDesc =>
      'Juża l-GPS awtomatikament biss jekk il-permess tal-post ikun diġà ngħata';

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
  String get settingsImperialUnits => 'Unitajiet imperjali';

  @override
  String get settingsImperialUnitsDesc =>
      'Mili u piedi minflok kilometri u metri';

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
      'Roadstr hija applikazzjoni ta\' navigazzjoni sperimentali, b\'sors miftuħ, immantnuta mill-komunità, ibbażata fuq data tal-OpenStreetMap u l-protokoll Nostr, disponibbli għall-użu fi kwalunkwe pajjiż. Billi tniżżel, tinstalla jew tuża din l-applikazzjoni, l-utent jaċċetta bla kundizzjoni t-termini kollha li ġejjin, mingħajr limitazzjoni ta\' territorju.\n\n🚗 IS-SIGURTÀ TAT-TRIQ L-EWWEL\nIs-sewwieq għandu dejjem iżomm għajnejh fit-triq u jikkonforma mal-liġijiet kollha applikabbli tat-traffiku u s-sinjaletika murija, li dejjem għandhom preċedenza fuq kwalunkwe struzzjoni mill-applikazzjoni. Qatt m\'għandek topera l-apparat waqt is-sewqan; waħħlu f\'sapport approvat u viżibbli qabel titlaq, u qatt m\'għandek titbiegħed b\'attenzjonek mit-triq biex tinteraġixxi miegħu waqt li l-vettura tkun miexja.\n\n⚠️ ASSUNZJONI TAR-RISKJU — MADWAR ID-DINJA\nBl-użu ta\' Roadstr, fi kwalunkwe pajjiż u taħt kwalunkwe sistema legali, l-utent, b\'għarfien u volontarjament, jassumi r-riskji KOLLHA marbuta mal-użu tagħha, inklużi iżda mhux limitati għal: inċidenti tat-traffiku, korriment personali, mewt, ħsara fil-proprjetà, ħsara fil-vettura, multi, pieni amministrattivi, ġbid tal-vettura, sekwestru, responsabbiltà kriminali, jew kwalunkwe konsegwenza oħra li tirriżulta direttament jew indirettament mill-fatt li wieħed jistrieħ fuq l-applikazzjoni. L-utent waħdu jġorr ir-responsabbiltà sħiħa għal kull deċiżjoni ta\' sewqan u navigazzjoni.\n\n🚫 EBDA GARANZIJA\nRoadstr hija pprovduta strettament \"KIF INHI\" u \"SKONT ID-DISPONIBBILTÀ\", mingħajr ebda garanzija ta\' kwalunkwe tip, kemm jekk espressa, implikata jew statutorja — inklużi, mingħajr limitazzjoni, garanziji ta\' preċiżjoni, kompletezza, affidabbiltà, disponibbiltà, kummerċjabbiltà, adegwatezza għal skop partikolari, u nuqqas ta\' ksur. Id-data tal-mapep, l-ippjanar tar-rotot, il-limiti tal-veloċità, il-kameras tal-veloċità, u l-informazzjoni dwar iż-żoni ta\' traffiku ristrett (ZTL/ZAC/LTZ) ġejjin minn sorsi miftuħa, immantnuti mill-komunità (OpenStreetMap, Overpass API) li jistgħu jkunu mhux kompluti, skaduti jew mhux preċiżi għal kwalunkwe pajjiż, reġjun jew muniċipalità, fi kwalunkwe ħin u mingħajr avviż. L-utent hu unikament responsabbli li jivverifika b\'mod indipendenti, qabel u matul l-ivvjaġġar, il-legalità u l-aċċessibbiltà ta\' kwalunkwe rotta suġġerita mas-sinjaletika u r-regolamenti lokali uffiċjali.\n\n📍 PREĊIŻJONI U GPS\nIl-pożizzjonament GPS jista\' jkun mhux preċiż jew mhux disponibbli. Id-direzzjonijiet, id-distanzi, u l-allerti kollha huma pprovduti biss bħala gwida u qatt m\'għandhom jitqiesu bħala l-uniku bażi għal deċiżjoni ta\' sewqan.\n\n🛡️ LIMITAZZJONI TAR-RESPONSABBILTÀ\nSal-punt massimu permess mil-liġi applikabbli fi kwalunkwe ġurisdizzjoni, l-iżviluppaturi, il-kontributuri, u kwalunkwe parti involuta fil-ħolqien jew id-distribuzzjoni ta\' Roadstr m\'għandhomx ikunu responsabbli għal kwalunkwe dannu dirett, indirett, inċidentali, konsegwenzjali, speċjali, eżemplari jew punittiv ta\' kwalunkwe tip — inkluż korriment personali, mewt, jew telf finanzjarju — li jirriżulta minn jew relatat mal-użu jew l-inkapaċità li tuża l-applikazzjoni, anke jekk avżati bil-possibbiltà ta\' tali danni. Fejn ġurisdizzjoni ma tippermettix parti minn din il-limitazzjoni jew kollha kemm hi, ir-responsabbiltà hija limitata sal-iżgħar punt permess mil-liġi f\'dik il-ġurisdizzjoni.\n\n🤝 INDENNIZZ\nL-utent jaqbel li jindennizza u jżomm bla ħsara lill-iżviluppaturi u l-kontributuri minn kwalunkwe pretensjoni, dannu, telf, jew spiża (inklużi l-ispejjeż legali) li jirriżultaw mill-użu tal-applikazzjoni mill-utent jew minn ksur ta\' dawn it-termini.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ SEPARABBILTÀ\nJekk xi dispożizzjoni ta\' dawn it-termini tinstab li mhix infurzabbli f\'ġurisdizzjoni partikolari, dik id-dispożizzjoni għandha tiġi limitata jew separata sal-punt minimu meħtieġ, u d-dispożizzjonijiet li jifdal jibqgħu fis-seħħ sħiħ.\n\nBl-użu ta\' Roadstr, kullimkien fid-dinja, l-utent jikkonferma li qara, fehem, u aċċetta bla kundizzjoni kull terminu msemmi hawn fuq, u jassumi responsabbiltà sħiħa u kompluta — u r-riskju kollu — għall-użu tal-applikazzjoni u kwalunkwe konsegwenza li tirriżulta minnha.';

  @override
  String get readOnWikipedia => 'Aqra fuq Wikipedija';

  @override
  String get openInBrowser => 'Iftaħ fil-brawżer';

  @override
  String get wikipediaLoadFailed =>
      'Il-Wikipedia ma setgħetx titgħabba b’mod sigur.';

  @override
  String get retry => 'Erġa’ pprova';

  @override
  String get poiDetailsFromOsm => 'Informazzjoni minn OpenStreetMap';

  @override
  String get poiCategory => 'Kategorija';

  @override
  String get poiOperator => 'Operatur';

  @override
  String get poiCuisine => 'Kċina';

  @override
  String get poiAccessibility => 'Aċċessibbiltà';

  @override
  String get poiWheelchairYes => 'Aċċessibbli bis-siġġu tar-roti';

  @override
  String get poiWheelchairLimited => 'Aċċess limitat bis-siġġu tar-roti';

  @override
  String get poiWheelchairNo => 'Mhux aċċessibbli bis-siġġu tar-roti';

  @override
  String get poiContact => 'Kuntatt';

  @override
  String get poiAddress => 'Indirizz';

  @override
  String get poiWebsite => 'Websajt';

  @override
  String get poiAccessPrivate => 'Aċċess privat';

  @override
  String get poiAccessCustomers => 'Għall-klijenti biss';

  @override
  String get poiAccessPermit => 'Meħtieġ permess';

  @override
  String get poiAccessNo => 'Ebda aċċess pubbliku';

  @override
  String get poiAccessDestination => 'Aċċess għad-destinazzjoni biss';

  @override
  String get poiLightningAccepted => 'Jaċċetta Lightning';

  @override
  String get poiBitcoinAccepted => 'Jaċċetta Bitcoin';

  @override
  String get poiParkingDetails => 'Parkeġġ';

  @override
  String get poiParkingSurface => 'Fil-wiċċ';

  @override
  String get poiParkingUnderground => 'Taħt l-art';

  @override
  String get poiParkingMultiStorey => 'B\'ħafna sulari';

  @override
  String get poiParkingStreetSide => 'Maġenb it-triq';

  @override
  String get poiParkingLane => 'Fit-triq';

  @override
  String get poiParkingRooftop => 'Fuq il-bejt';

  @override
  String get poiFee => 'Tariffa';

  @override
  String get poiFree => 'B\'xejn';

  @override
  String get poiPaid => 'Bi ħlas';

  @override
  String get poiCapacity => 'Kapaċità';

  @override
  String get poiMaxStay => 'Żjara massima';

  @override
  String get poiPrice => 'Prezz';

  @override
  String get poiChargingDetails => 'Iċċarġjar';

  @override
  String get poiConnectorType2 => 'Tip 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Diżil';

  @override
  String get poiPetrol95 => 'Petrol 95';

  @override
  String get poiSmokingAllowed => 'It-tipjip permess';

  @override
  String get poiSmokingOutside => 'Tipjip barra';

  @override
  String get poiSmokingAreas => 'Żoni għat-tipjip';

  @override
  String get poiSmokeFree => 'Mingħajr tipjip';

  @override
  String get poiOutdoorSeating => 'Postijiet bilqiegħda barra';

  @override
  String get poiTakeaway => 'Takeaway';

  @override
  String get poiTakeawayOnly => 'Takeaway biss';

  @override
  String get gpsSignalLost => 'Is-sinjal GPS intilef';

  @override
  String get poiOpenNow => 'Miftuħ issa';

  @override
  String get poiClosedNow => 'Magħluq';

  @override
  String poiOpensAt(String when) {
    return 'Jiftaħ: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Jagħlaq: $when';
  }

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
      'Żona ta\' traffiku ristrett \'il quddiem — ir-rotta tgħaddi minnha';

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
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

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

  @override
  String get kokoroVoiceGenderTitle => 'Vuċi';

  @override
  String get kokoroVoiceFemale => 'Femminili';

  @override
  String get kokoroVoiceMale => 'Maskili';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Vuċi maskili mhix disponibbli għal din il-lingwa';

  @override
  String get kokoroSpeedTitle => 'Veloċità tad-diskors';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Fakultattiv: il-favoriti salvati jistgħu jiġu sinkronizzati bejn l-apparati tiegħek permezz tar-relays Nostr, kriptati end-to-end (NIP-44) biċ-ċavetta tiegħek stess — ir-relays jaraw biss test kriptat, u ħadd ħliefek ma jista’ jaqra l-kontenut. Attivah meta trid mis-Settings.';

  @override
  String get parkingSaveHere => 'Issejvja l-parkeġġ hawn';

  @override
  String get parkingMarkerTitle => 'Post tal-parkeġġ';

  @override
  String get parkingNavigateHere => 'Innaviga lejn il-parkeġġ';

  @override
  String get parkingRemove => 'Neħħi l-parkeġġ';

  @override
  String get parkingSavedSnack => 'Il-post tal-parkeġġ ġie salvat';

  @override
  String get parkingRemovedSnack => 'Il-post tal-parkeġġ tneħħa';

  @override
  String get exportFavoritesTitle => 'Esporta l-favoriti';

  @override
  String get exportFavoritesDesc =>
      'Issejvja l-postijiet favoriti tiegħek f’fajl JSON li tista’ tagħmillu backup jew tittrasferih għal apparat ieħor.';

  @override
  String get exportEncryptToggle => 'Ikkripta b’password';

  @override
  String get exportPasswordHint => 'Password';

  @override
  String get exportButton => 'Esporta';

  @override
  String get exportSuccessSnack => 'Il-favoriti ġew esportati';

  @override
  String get exportFailedSnack => 'L-esportazzjoni falliet';

  @override
  String get importFavoritesTitle => 'Importa l-favoriti';

  @override
  String get importPasswordPrompt =>
      'Dan il-fajl huwa kriptat — daħħal il-password';

  @override
  String importSuccessSnack(int n) {
    return '$n favoriti importati';
  }

  @override
  String get importFailedSnack =>
      'L-importazzjoni falliet — iċċekkja l-fajl u l-password';

  @override
  String get syncFavoritesTitle => 'Sinkronizza l-favoriti';

  @override
  String get syncFavoritesDesc =>
      'Ippubblika l-favoriti tiegħek, kriptati end-to-end, fuq ir-relays Nostr tiegħek biex isegwuk fuq l-apparati kollha. Ir-relays jaraw biss test kriptat — ħadd ħliefek ma jista’ jaqra l-kontenut.';

  @override
  String get syncNowButton => 'Ibgħat lil Nostr';

  @override
  String get syncPullButton => 'Ġib minn Nostr';

  @override
  String get syncPassphraseTitle =>
      'Frażi sigrieta tas-sinkronizzazzjoni (mhux obbligatorja)';

  @override
  String get syncPassphraseDesc =>
      'It-tieni saff ta\' kriptaġġ għall-favoriti sinkronizzati: anke jekk iċ-ċavetta Nostr tiegħek tiġi kompromessa, l-istampa fuq ir-relays tibqa\' ma tinqarax mingħajr din il-frażi. Iddaħħalha darba fuq kull apparat ġdid. Ħalli vojt biex titfi.';

  @override
  String get syncSuccessSnack => 'Il-favoriti ġew sinkronizzati';

  @override
  String get syncFailedSnack => 'Is-sinkronizzazzjoni falliet';

  @override
  String syncLastSyncLabel(String when) {
    return 'L-aħħar sinkronizzazzjoni: $when';
  }

  @override
  String get syncNoIdentity =>
      'Idħol b’Nostr (Settings → Profil) biex tattiva s-sinkronizzazzjoni';

  @override
  String get onboardingVpnNotice =>
      'Għal privatezza massima — ir-rapporti jixterdu fin-netwerk Nostr — huwa rrakkomandat l-użu ta\' VPN. Mullvad, li tista\' tħallas bil-Bitcoin, hija l-għażla rrakkomandata.';

  @override
  String get trafficNormal => 'Traffiku normali';

  @override
  String get trafficModerate => 'Traffiku moderat';

  @override
  String get trafficHeavy => 'Traffiku qawwi';

  @override
  String get avoidHighwaysChip => 'Evita l-awtostradi';

  @override
  String get avoidTollsChip => 'Evita l-pedaġġi';

  @override
  String get preferShorterChip => 'L-iqsar rotta';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Uri l-previżjoni tar-rotta';

  @override
  String get avoidHighwaysAndTolls => 'Evita awtostradi u toroq bi ħlas';

  @override
  String get avoidRouteUnavailable =>
      'Ma nstabitx rotta li tevita kemm l-awtostradi kif ukoll it-toroq bi ħlas.';

  @override
  String get avoidanceUnavoidableSection =>
      'Inaqqas awtostradi/toroq bi ħlas · parti inevitabbli';
}
