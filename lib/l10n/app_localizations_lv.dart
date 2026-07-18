// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Latvian (`lv`).
class AppLocalizationsLv extends AppLocalizations {
  AppLocalizationsLv([String locale = 'lv']) : super(locale);

  @override
  String get searchHint => 'Kurp vēlaties doties?';

  @override
  String get calculatingRoute => 'Aprēķina maršrutu…';

  @override
  String get routeNotFoundTitle => 'Maršruts nav atrasts';

  @override
  String get noRouteFound => 'Maršruts nav atrasts. Pārbaudiet savienojumu.';

  @override
  String get emptyServerResponse =>
      'Tukša servera atbilde. Pārbaudiet savienojumu.';

  @override
  String routeError(String error) {
    return 'Maršruta aprēķināšanas kļūda: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS nav pieejams — Iestatījumi → Lietotne → Roadstr → Atļaujas → Atrašanās vieta';

  @override
  String get acquiringGps => 'Iegūst GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopper serveris nav konfigurēts — izmanto OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper API atslēga nav konfigurēta — izmanto OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService API atslēga nav konfigurēta — izmanto OSRM';

  @override
  String get chooseRoute => 'Izvēlēties maršrutu';

  @override
  String routeOptionsCount(int count) {
    return '$count iespējas';
  }

  @override
  String get cancel => 'Atcelt';

  @override
  String get startNavigation => 'Sākt navigāciju';

  @override
  String get fastestRoute => 'Ātrākais';

  @override
  String get now => 'Tagad';

  @override
  String get history => 'Vēsture';

  @override
  String get clearHistory => 'Notīrīt';

  @override
  String get selectedPosition => 'Atlasītā pozīcija';

  @override
  String get bottomBarProfile => 'Profils';

  @override
  String get bottomBarMenu => 'Izvēlne';

  @override
  String get settingsTitle => 'Iestatījumi';

  @override
  String get sectionTheme => 'Motīvs';

  @override
  String get sectionMap => 'Karte';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Valoda';

  @override
  String get themeLightNostr => 'Gaišs · Nostr Violets';

  @override
  String get themeLightBitcoin => 'Gaišs · Bitcoin Oranžs';

  @override
  String get themeDarkNostr => 'Tumšs · Nostr Violets';

  @override
  String get themeDarkBitcoin => 'Tumšs · Bitcoin Oranžs';

  @override
  String get langSystem => 'Sistēmas noklusējums';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Paturēt ekrānu ieslēgtu';

  @override
  String get keepScreenOnDescription => 'Novērš miega režīmu navigācijas laikā';

  @override
  String get autoCenterOnLaunch => 'Palaižot centrēt uz manu atrašanās vietu';

  @override
  String get autoCenterOnLaunchDesc =>
      'Automātiski izmanto GPS tikai tad, ja atrašanās vietas atļauja jau ir piešķirta';

  @override
  String get rotateMap => 'Karte seko virzienam';

  @override
  String get rotateMapDescription =>
      'Karte griežas atbilstoši braukšanas virzienam';

  @override
  String get mapTileUrlLabel => 'Kartes flīžu URL';

  @override
  String get routingProviderLabel => 'Maršrutēšanas pakalpojumu sniedzējs';

  @override
  String get osrmProvider => 'OSRM (publisks, nav nepieciešama atslēga)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (vietējais/privātais)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API atslēga)';

  @override
  String get openrouteProvider => 'OpenRouteService (API atslēga)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API atslēga (neobligāti)';

  @override
  String get verify => 'Pārbaudīt';

  @override
  String get graphhopperServerUrlRequired =>
      'Pirms pārbaudes ievadiet servera URL.';

  @override
  String get successTitle => 'Veiksmīgi';

  @override
  String get graphhopperServerReachable => 'GraphHopper serveris ir pieejams';

  @override
  String get errorTitle => 'Kļūda';

  @override
  String get close => 'Aizvērt';

  @override
  String get infoVersion => 'Versija';

  @override
  String get infoProtocol => 'Protokols';

  @override
  String get infoMaps => 'Kartes';

  @override
  String get infoRouting => 'Maršrutēšana';

  @override
  String get infoSource => 'Avots';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (pašmitināts)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (mākonis)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profils';

  @override
  String get notConnected => 'Nav savienots';

  @override
  String get loginWithNostrTitle => 'PIETEIKTIES AR NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Privātā atslēga nekad neatstāj jūsu ierīci · Ieteicams';

  @override
  String get nsecLoginOption => 'Ievietot nsec';

  @override
  String get nsecLoginDescription =>
      'Privātā atslēga tiek glabāta lokāli · Mazāk droši';

  @override
  String get connectedViaAmber => 'Savienots, izmantojot Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Savienots, izmantojot nsec';

  @override
  String get publicKeyLabel => 'PUBLISKĀ ATSLĒGA';

  @override
  String get npubCopiedToClipboard => 'npub nokopēts starpliktuvē';

  @override
  String get logoutTitle => 'Atvienot';

  @override
  String get logoutConfirmation =>
      'Noņemt Nostr akreditācijas datus no šīs ierīces?';

  @override
  String get logoutButton => 'Atvienot';

  @override
  String get nostrIdentityInfo =>
      'Ar Nostr identitāti varat publicēt satiksmes brīdinājumus, pārskatus un interesantas vietas decentralizētā veidā Nostr tīklā bez centrāliem serveriem.';

  @override
  String get warningTitle => 'Brīdinājums';

  @override
  String get nsecWarning =>
      'Jūs gatavojaties ievadīt savu Nostr privāto atslēgu tieši lietotnē. Ikviens, kam ir fiziska vai attālināta piekļuve jūsu ierīcei, var to nolasīt un pastāvīgi pārņemt kontroli pār jūsu Nostr identitāti.';

  @override
  String get amberSecureMethodHint =>
      '✦  Drošā metode ir Amber (NIP-55): nsec nekad neatstāj lietotnes parakstītāja glabātuvi.';

  @override
  String get nsecRiskAcknowledgment => 'Saprotu risku un tāpat vēlos turpināt';

  @override
  String get continueButton => 'Turpināt';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber ir NIP-55 saderīga Android lietotņu parakstītāja lietotne. Jūsu privātā atslēga paliek izolēta Amber iekšienē un netiek kopīgota.';

  @override
  String get requestKeyFromAmber => 'Pieprasīt publisko atslēgu no Amber';

  @override
  String get amberNotFound =>
      'Amber nav atrasts. Instalējiet to no Play Store vai manuāli ievadiet savu npub.';

  @override
  String get waitingForAmberResponse => 'Gaida Amber atbildi…';

  @override
  String get pasteNpubManually => 'Ielīmējiet savu npub manuāli:';

  @override
  String get confirmNpub => 'Apstiprināt npub';

  @override
  String get enterNsecTitle => 'Ievietot nsec';

  @override
  String get loginButton => 'Pieteikties';

  @override
  String get invalidNpubTitle => 'Nederīgs npub';

  @override
  String get invalidNsecTitle => 'Nederīgs nsec';

  @override
  String get invalidNpubMessage =>
      'Pārliecinieties, ka esat ielīmējis pareizo npub.';

  @override
  String get invalidNsecMessage =>
      'Pārliecinieties, ka esat ielīmējis pareizo nsec.';

  @override
  String get amberResponseError => 'Amber atbildes kļūda';

  @override
  String get ok => 'Labi';

  @override
  String get or => 'vai';

  @override
  String get gpsNotActiveTitle => 'GPS nav aktīvs';

  @override
  String get gpsDisabledMessage =>
      'Ieslēdziet GPS ierīces iestatījumos, lai noteiktu savu atrašanās vietu un izmantotu navigāciju.';

  @override
  String get openSettings => 'Iestatījumi';

  @override
  String get myLocation => 'Mana atrašanās vieta';

  @override
  String get loginToReport =>
      'Piesakieties ar Nostr (Profila sadaļa), lai ziņotu par notikumiem';

  @override
  String get navigateHere => 'Navigēt uz šejieni';

  @override
  String get reportEventHere => 'Ziņot par notikumu šeit';

  @override
  String get zapSendSats => 'Zap ⚡ (nosūtīt sats)';

  @override
  String get sendZap => 'Nosūtīt Zap';

  @override
  String get chooseAmountSats => 'Izvēlieties summu satoshi (sats):';

  @override
  String get customAmount => 'Pielāgota summa…';

  @override
  String get zapSending => 'Sūta…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Iegūst Lightning adresi…';

  @override
  String get noLightningAddress => 'Šim ziņotājam nav Lightning adreses';

  @override
  String get requestingInvoice => 'Pieprasa rēķinu…';

  @override
  String get lnurlUnavailable => 'LNURL nav pieejams';

  @override
  String get invoiceFailed => 'Neizdevās ģenerēt rēķinu';

  @override
  String get openingWallet => 'Atver maku…';

  @override
  String get payingViaNwc => 'Maksā, izmantojot NWC…';

  @override
  String get noLightningWallet => 'Lightning maks nav atrasts';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats nosūtīti!';
  }

  @override
  String get stillThere => 'Joprojām tur';

  @override
  String get notThereAnymore => 'Vairs tur nav';

  @override
  String get loginToConfirm =>
      'Piesakieties ar Nostr, lai apstiprinātu vai apstrīdētu';

  @override
  String get reportAnEvent => 'Ziņot par notikumu';

  @override
  String get optionalComment => 'Neobligāts komentārs…';

  @override
  String get publish => 'Publicēt';

  @override
  String get publishing => 'Publicē…';

  @override
  String get reportPublished => 'Ziņojums publicēts ✓';

  @override
  String get myReports => 'MANI ZIŅOJUMI';

  @override
  String get noReportsYet => 'Nav publicētu ziņojumu';

  @override
  String get zapBalance => 'Zap atlikums';

  @override
  String get satoshiFromReports => 'Satoshi saņemti no jūsu ziņojumiem';

  @override
  String get reputationHigh => 'Augsta';

  @override
  String get reputationMedium => 'Vidēja';

  @override
  String get reputationLow => 'Zema';

  @override
  String reputationLabel(String level) {
    return 'Reputācija $level';
  }

  @override
  String reliability(int pct) {
    return 'Uzticamība: $pct%';
  }

  @override
  String get confirmedLabel => 'Apstiprināts';

  @override
  String get removedLabel => 'Noņemts';

  @override
  String get positionLabel => 'Pozīcija';

  @override
  String get loadingLabel => 'Ielādē…';

  @override
  String get sectionWebSearch => 'Tīmekļa meklēšana';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Ielīmējiet savu NWC URI (Alby Hub, Mutiny, Cashu…), lai maksātu Zaps tieši no lietotnes.';

  @override
  String get searchEngineQwantDesc =>
      'Eiropas, privātums pirmajā vietā. Bez izsekošanas, bez reklāmas profiliem. Ieteicams.';

  @override
  String get searchEngineBraveDesc =>
      'Neatkarīgs indekss, atvērtā koda. Nav atkarīgs no Google vai Bing. Nulles profilēšana.';

  @override
  String get searchEngineDdgDesc =>
      'Vērsts uz privātumu un populārs. Rezultāti daļēji no Bing — paturiet to prātā.';

  @override
  String get searchEngineStartpageDesc =>
      'Google rezultāti, nenododot jūsu datus Google. Saprātīgs kompromiss.';

  @override
  String get searchEngineGoogleDesc =>
      'Ļoti efektīvs. Bet atcerieties: Google pazīst jūs labāk nekā jūsu māte un pārdod visu reklāmdevējiem. Jūsu izvēle. 🍪';

  @override
  String get categoryPolice => 'Policija';

  @override
  String get categorySpeedCamera => 'Ātruma kamera';

  @override
  String get categoryTrafficJam => 'Sastrēgums';

  @override
  String get categoryAccident => 'Avārija';

  @override
  String get categoryRoadClosure => 'Ceļa slēgšana';

  @override
  String get categoryConstruction => 'Ceļu darbi';

  @override
  String get categoryHazard => 'Bīstamība';

  @override
  String get categoryRoadCondition => 'Ceļa stāvoklis';

  @override
  String get categoryPothole => 'Bedre';

  @override
  String get categoryFog => 'Migla';

  @override
  String get categoryIce => 'Ledus';

  @override
  String get categoryAnimal => 'Dzīvnieks';

  @override
  String get categoryOther => 'Cits';

  @override
  String get dateTimeLabel => 'Datums / laiks';

  @override
  String minutesAgo(int count) {
    return 'pirms $count min';
  }

  @override
  String hoursAgo(int count) {
    return 'pirms ${count}h';
  }

  @override
  String daysAgo(int count) {
    return 'pirms ${count}d';
  }

  @override
  String get sectionFavorites => 'Saglabātās vietas';

  @override
  String get addFavorite => 'Pievienot vietu';

  @override
  String get favoriteLabelHint => 'Nosaukums (piem. Mājas, Birojs)';

  @override
  String get favoriteAddressHint => 'Adrese';

  @override
  String get favoriteGeocodingError =>
      'Adrese nav atrasta. Mēģini precīzāku adresi.';

  @override
  String get trafficAlertTitle => 'Jauna satiksme maršrutā';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category ziņots $age jūsu maršrutā.\n\nVai vēlaties atrast alternatīvu maršrutu?';
  }

  @override
  String get trafficContinue => 'Turpināt';

  @override
  String get trafficRecalculate => 'Pārrēķināt maršrutu';

  @override
  String get navExitTitle => 'Iziet no navigācijas?';

  @override
  String get navExitBody =>
      'Vai vēlaties apturēt navigāciju un atgriezties kartē?';

  @override
  String get navContinue => 'Turpināt navigāciju';

  @override
  String get navExit => 'Jā, iziet';

  @override
  String get loadingInfo => 'Ielādē informāciju…';

  @override
  String get conditionsOnRoute => 'Apstākļi maršrutā';

  @override
  String get calculateRoute => 'Aprēķināt maršrutu';

  @override
  String get sectionNavigationVoice => 'Navigācijas balss';

  @override
  String get voiceGuidance => 'Balss vadība';

  @override
  String get voiceGuidanceDesc =>
      'Skaļi nolasīt pagrieziena norādes navigācijas laikā';

  @override
  String get testVoiceEngine => 'Testēt balss dzinēju';

  @override
  String get testVoiceEngineDesc =>
      'Pieskarieties, lai pārbaudītu TTS dzinēju un saņemtu iestatīšanas norādījumus';

  @override
  String get ttsDialogTitle => 'Trūkst balss dzinēja';

  @override
  String get ttsDialogBody =>
      'Netika atrasts neviens darbojošies Text-to-Speech dzinējs.\n\n“Roadstr” paļaujas tikai uz atvērtā koda programmatūru — instalējiet kādu no šiem bezmaksas dzinējiem no F-Droid:';

  @override
  String get ttsRhvoiceDesc =>
      'Dabiski skanoša balss, ierobežots valodu saraksts';

  @override
  String get ttsEspeakDesc => 'Aptver vairāk nekā 100 valodas, robotiska balss';

  @override
  String get ttsInstallNote =>
      '⚠️ Pēc instalēšanas:\n1. Android iestatījumi → Pieejamība → Teksta pārvēršana runā\n2. Izvēlieties tikko instalēto dzinēju\n3. Lejupielādējiet savas valodas balss datus\n4. Pilnībā restartējiet “Roadstr”';

  @override
  String get ttsTestNow => 'Testēt tagad';

  @override
  String get voiceUnsupportedTitle => 'Balss vadība nav pieejama';

  @override
  String get voiceUnsupportedBody =>
      'Jūsu valoda vēl netiek atbalstīta balss pagrieziena norādēm. Navigācijas norādes joprojām tiks rādītas kā teksts ekrānā.';

  @override
  String get kokoroModelTitle => 'Balss modelis (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Nav lejupielādēts · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Lejupielāde...';

  @override
  String get kokoroModelStatusReady => 'Balss modelis ir gatavs';

  @override
  String get kokoroModelDownloadBtn => 'Lejupielādēt';

  @override
  String get kokoroModelSupportedLangs =>
      'Atbalsta: itāliešu, angļu, spāņu, franču, japāņu, ķīniešu, portugāļu';

  @override
  String get autoDarkMode => 'Automātiskā tumšā tēma';

  @override
  String get autoDarkModeDesc => 'Aktivizē tumšo tēmu saulrieta laikā';

  @override
  String get settingsImperialUnits => 'Imperiālās mērvienības';

  @override
  String get settingsImperialUnitsDesc =>
      'Jūdzes un pēdas, nevis kilometri un metri';

  @override
  String get arrivedTitle => '🎉 Esat ieradušies!';

  @override
  String get arrivedBody => 'Esat sasniedzis savu galamērķi.';

  @override
  String get arrivedFeedbackPrompt => 'Kā gāja?';

  @override
  String get feedbackBad => 'Slikti';

  @override
  String get feedbackGood => 'Labi!';

  @override
  String get feedbackDialogTitle => 'Pastāstiet mums, kas nogāja greizi';

  @override
  String get feedbackHint => 'Aprakstiet problēmu…';

  @override
  String get feedbackSent => 'Atsauksme nosūtīta — paldies! 🙏';

  @override
  String get feedbackSubmit => 'Sūtīt';

  @override
  String get transportModeCar => 'Automašīna';

  @override
  String get transportModeWalk => 'Kājām';

  @override
  String etaArrivalLabel(String time) {
    return 'Ierašanās $time';
  }

  @override
  String get supportRoadstr => 'Atbalstīt Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address nokopēts starpliktuvē';
  }

  @override
  String get disclaimerTitle => 'Svarīgs paziņojums';

  @override
  String get disclaimerAccept => 'Esmu izlasījis(-usi) un piekrītu';

  @override
  String get disclaimerBody =>
      'Roadstr ir eksperimentāla, atvērtā koda, kopienas uzturēta navigācijas lietotne, kas balstīta uz OpenStreetMap datiem un Nostr protokolu un ir pieejama lietošanai jebkurā valstī. Lejupielādējot, instalējot vai lietojot šo lietotni, lietotājs beznosacījumu kārtā piekrīt visiem tālāk minētajiem noteikumiem bez teritoriāliem ierobežojumiem.\n\n🚗 SATIKSMES DROŠĪBA VISPIRMS\nVadītājam vienmēr jāskatās uz ceļu un jāievēro visi piemērojamie ceļu satiksmes noteikumi un uzstādītās ceļa zīmes, kurām vienmēr ir prioritāte pār jebkuru lietotnes norādi. Nekad nedarbojieties ar ierīci, vadot transportlīdzekli; pirms brauciena sākuma nostipriniet to apstiprinātā, labi redzamā turētājā, un, transportlīdzeklim kustoties, nekad nenovērsiet uzmanību no ceļa, lai mijiedarbotos ar ierīci.\n\n⚠️ RISKA UZŅEMŠANĀS — VISĀ PASAULĒ\nIzmantojot Roadstr jebkurā valstī un saskaņā ar jebkuru tiesisko sistēmu, lietotājs apzināti un labprātīgi uzņemas VISUS riskus, kas saistīti ar tās lietošanu, tostarp, bet neaprobežojoties ar: ceļu satiksmes negadījumiem, miesas bojājumiem, nāvi, mantas bojājumiem, transportlīdzekļa bojājumiem, naudas sodiem, administratīvajiem sodiem, evakuāciju, aizturēšanu, kriminālatbildību vai jebkurām citām sekām, kas tieši vai netieši izriet no paļaušanās uz lietotni. Lietotājs vienpersoniski uzņemas pilnu atbildību par katru vadīšanas un navigācijas lēmumu.\n\n🚫 GARANTIJAS NEESAMĪBA\nRoadstr tiek nodrošināta stingri „TĀDA, KĀDA TĀ IR” un „KĀ PIEEJAMA” bāzē, bez jebkādas garantijas — ne tiešas, ne netiešas, ne likumā noteiktas —, tostarp, bez ierobežojumiem, garantijām par precizitāti, pilnīgumu, uzticamību, pieejamību, piemērotību pārdošanai, piemērotību konkrētam mērķim un tiesību nepārkāpšanu. Kartes dati, maršrutēšana, ātruma ierobežojumi, ātruma kontroles kameras un satiksmes ierobežojuma zonu (ZTL/ZAC/LTZ) informācija tiek iegūta no atvērtiem, kopienas uzturētiem avotiem (OpenStreetMap, Overpass API), kas var būt nepilnīgi, novecojuši vai neprecīzi jebkurai valstij, reģionam vai pašvaldībai, jebkurā laikā un bez brīdinājuma. Lietotājs ir vienīgais atbildīgais par to, lai pirms brauciena un tā laikā patstāvīgi pārbaudītu jebkura ieteiktā maršruta likumību un caurbraucamību, salīdzinot to ar oficiālajām vietējām ceļa zīmēm un noteikumiem.\n\n📍 PRECIZITĀTE UN GPS\nGPS pozicionēšana var būt neprecīza vai nepieejama. Visi norādījumi, attālumi un brīdinājumi tiek sniegti tikai orientējošiem nolūkiem, un tie nekad nedrīkst būt vienīgais pamats vadīšanas lēmumam.\n\n🛡️ ATBILDĪBAS IEROBEŽOJUMS\nCiktāl to pieļauj piemērojamie tiesību akti jebkurā jurisdikcijā, izstrādātāji, līdzstrādnieki un jebkura persona, kas iesaistīta Roadstr izveidē vai izplatīšanā, nav atbildīga par jebkādiem tiešiem, netiešiem, nejaušiem, izrietošiem, īpašiem, priekšzīmīgiem vai sodošiem zaudējumiem, tostarp miesas bojājumiem, nāvi vai finansiāliem zaudējumiem, kas rodas no lietotnes lietošanas vai nespējas to lietot vai ir ar to saistīti, pat ja tie ir brīdināti par šādu zaudējumu iespējamību. Ja kādā jurisdikcijā šis ierobežojums daļēji vai pilnībā nav pieļaujams, atbildība tiek ierobežota līdz mazākajam likumā šajā jurisdikcijā pieļautajam apmēram.\n\n🤝 ZAUDĒJUMU ATLĪDZINĀŠANA\nLietotājs piekrīt atlīdzināt zaudējumus un pasargāt izstrādātājus un līdzstrādniekus no jebkādām prasībām, zaudējumiem, izmaksām vai izdevumiem (tostarp juridiskajām izmaksām), kas rodas no lietotāja veiktās lietotnes lietošanas vai šo noteikumu pārkāpuma.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ ATDALĀMĪBA\nJa kāds no šo noteikumu nosacījumiem konkrētā jurisdikcijā tiek atzīts par neizpildāmu, šis nosacījums tiek ierobežots vai atdalīts minimāli nepieciešamajā apmērā, un visi pārējie nosacījumi paliek pilnībā spēkā.\n\nIzmantojot Roadstr jebkurā pasaules vietā, lietotājs apliecina, ka ir izlasījis, sapratis un beznosacījumu kārtā pieņēmis visus iepriekš minētos noteikumus, un uzņemas pilnu un pilnīgu atbildību — un visus riskus — par lietotnes lietošanu un jebkurām no tās izrietošām sekām.';

  @override
  String get readOnWikipedia => 'Lasīt Vikipēdijā';

  @override
  String get openInBrowser => 'Atvērt pārlūkā';

  @override
  String get wikipediaLoadFailed => 'Wikipedia nevarēja droši ielādēt.';

  @override
  String get retry => 'Mēģināt vēlreiz';

  @override
  String get poiDetailsFromOsm => 'Informācija no OpenStreetMap';

  @override
  String get poiCategory => 'Kategorija';

  @override
  String get poiOperator => 'Operators';

  @override
  String get poiCuisine => 'Virtuve';

  @override
  String get poiAccessibility => 'Pieejamība';

  @override
  String get poiWheelchairYes => 'Pieejams ratiņkrēslā';

  @override
  String get poiWheelchairLimited => 'Ierobežota pieejamība ratiņkrēslā';

  @override
  String get poiWheelchairNo => 'Nav pieejams ratiņkrēslā';

  @override
  String get poiContact => 'Kontakti';

  @override
  String get poiAddress => 'Adrese';

  @override
  String get poiWebsite => 'Tīmekļa vietne';

  @override
  String get gpsSignalLost => 'Zaudēts GPS signāls';

  @override
  String get poiOpenNow => 'Tagad atvērts';

  @override
  String get poiClosedNow => 'Slēgts';

  @override
  String poiOpensAt(String when) {
    return 'Atveras: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Aizveras: $when';
  }

  @override
  String searchOnEngine(String engine) {
    return 'Meklēt $engine';
  }

  @override
  String get plannerFromHint => 'No…';

  @override
  String get plannerToHint => 'Galamērķis…';

  @override
  String departEta(String dep, String arr) {
    return 'Izbraukšana $dep  →  Ierašanās $arr';
  }

  @override
  String get modeCar => 'Auto';

  @override
  String get modeBike => 'Velosipēds';

  @override
  String get modeWalk => 'Kājām';

  @override
  String windSpeed(String speed) {
    return 'vējš $speed km/h';
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
  String get weatherClear => 'Skaidrs';

  @override
  String get weatherPartlyCloudy => 'Mainīgs';

  @override
  String get weatherCloudy => 'Mākoņains';

  @override
  String get weatherFog => 'Migla';

  @override
  String get weatherLightRain => 'Viegls lietus';

  @override
  String get weatherRain => 'Lietus';

  @override
  String get weatherSnow => 'Sniegs';

  @override
  String get weatherShowers => 'Lietusgāzes';

  @override
  String get weatherThunderstorm => 'Pērkona vētra';

  @override
  String get ztlAheadWarning =>
      'Priekšā ierobežotas satiksmes zona — maršruts iet cauri tai';

  @override
  String get ztlInsideWarning => 'Ierobežotas satiksmes zona';

  @override
  String get onboardingAppSubtitle => 'Atvērtā koda Nostr navigācija';

  @override
  String get onboardingWelcomeTitle => 'Laipni lūdzam';

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
  String get onboardingGetStarted => 'Sākt';

  @override
  String get onboardingNostrTitle => 'Nostr identitāte';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Savienots';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (ieteicams)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec atslēga';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Nederīga nsec atslēga — pārbaudiet un mēģiniet vēlreiz.';

  @override
  String get onboardingSkip => 'Izlaist pagaidām';

  @override
  String get onboardingContinue => 'Turpināt';

  @override
  String get onboardingEnterNsec => 'Ievadiet nsec atslēgu';

  @override
  String get onboardingSetupTitle => 'Iestatīt Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Atrašanās vieta';

  @override
  String get onboardingLocationGranted => 'Atrašanās vietas piekļuve piešķirta';

  @override
  String get onboardingLocationRequired => 'Nepieciešams GPS navigācijai';

  @override
  String get onboardingGrantButton => 'Piešķirt';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS lietotāji';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

  @override
  String get onboardingVoiceTitle => 'AI balss vadīšana (neobligāta)';

  @override
  String get onboardingVoiceReady => 'Kokoro balss modelis ir gatavs';

  @override
  String get onboardingVoiceDownloading => 'Lejupielādē balss modeli…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Lejupielādēt';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Esat gatavs navigācijai!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Braucam!';

  @override
  String get onboardingProfileLoading => 'Ielādē profilu…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Balss';

  @override
  String get kokoroVoiceFemale => 'Sievietes';

  @override
  String get kokoroVoiceMale => 'Vīrieša';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Vīrieša balss šai valodai nav pieejama';

  @override
  String get kokoroSpeedTitle => 'Runas ātrums';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Pēc izvēles: saglabātā izlase var sinhronizēties starp jūsu ierīcēm caur Nostr relejiem, pilnībā šifrēta (NIP-44) ar jūsu paša atslēgu — releji redz tikai šifrētu tekstu, un neviens cits kā jūs nevar lasīt saturu. Iespējojiet jebkurā laikā Iestatījumos.';

  @override
  String get parkingSaveHere => 'Saglabāt stāvvietu šeit';

  @override
  String get parkingMarkerTitle => 'Stāvvieta';

  @override
  String get parkingNavigateHere => 'Navigēt uz stāvvietu';

  @override
  String get parkingRemove => 'Noņemt stāvvietu';

  @override
  String get parkingSavedSnack => 'Stāvvieta saglabāta';

  @override
  String get parkingRemovedSnack => 'Stāvvieta noņemta';

  @override
  String get exportFavoritesTitle => 'Eksportēt izlasi';

  @override
  String get exportFavoritesDesc =>
      'Saglabājiet savas iecienītās vietas JSON failā, kuru varat dublēt vai pārnest uz citu ierīci.';

  @override
  String get exportEncryptToggle => 'Šifrēt ar paroli';

  @override
  String get exportPasswordHint => 'Parole';

  @override
  String get exportButton => 'Eksportēt';

  @override
  String get exportSuccessSnack => 'Izlase eksportēta';

  @override
  String get exportFailedSnack => 'Eksportēšana neizdevās';

  @override
  String get importFavoritesTitle => 'Importēt izlasi';

  @override
  String get importPasswordPrompt => 'Šis fails ir šifrēts — ievadiet paroli';

  @override
  String importSuccessSnack(int n) {
    return 'Importēti $n izlases ieraksti';
  }

  @override
  String get importFailedSnack =>
      'Importēšana neizdevās — pārbaudiet failu un paroli';

  @override
  String get syncFavoritesTitle => 'Sinhronizēt izlasi';

  @override
  String get syncFavoritesDesc =>
      'Publicējiet savu izlasi, pilnībā šifrētu, savos Nostr relejos, lai tā sekotu jums visās ierīcēs. Releji redz tikai šifrētu tekstu — neviens cits kā jūs nevar lasīt saturu.';

  @override
  String get syncNowButton => 'Nosūtīt uz Nostr';

  @override
  String get syncPullButton => 'Iegūt no Nostr';

  @override
  String get syncPassphraseTitle =>
      'Sinhronizācijas paroles frāze (neobligāta)';

  @override
  String get syncPassphraseDesc =>
      'Otrs šifrēšanas slānis sinhronizētajām izlasēm: pat ja jūsu Nostr atslēga tiktu kompromitēta, momentuzņēmums relejos bez šīs frāzes paliek nelasāms. Ievadīsiet to vienreiz katrā jaunā ierīcē. Atstājiet tukšu, lai atspējotu.';

  @override
  String get syncSuccessSnack => 'Izlase sinhronizēta';

  @override
  String get syncFailedSnack => 'Sinhronizācija neizdevās';

  @override
  String syncLastSyncLabel(String when) {
    return 'Pēdējā sinhronizācija: $when';
  }

  @override
  String get syncNoIdentity =>
      'Pierakstieties ar Nostr (Iestatījumi → Profils), lai iespējotu sinhronizāciju';

  @override
  String get onboardingVpnNotice =>
      'Maksimālai privātumam — ziņojumi tiek izplatīti Nostr tīklā — ieteicams izmantot VPN. Ieteicamā izvēle ir Mullvad, par kuru var maksāt ar Bitcoin.';

  @override
  String get trafficNormal => 'Parasta satiksme';

  @override
  String get trafficModerate => 'Mērena satiksme';

  @override
  String get trafficHeavy => 'Intensīva satiksme';

  @override
  String get avoidHighwaysChip => 'Izvairīties no automaģistrālēm';

  @override
  String get avoidTollsChip => 'Izvairīties no maksas ceļiem';

  @override
  String get preferShorterChip => 'Īsākais maršruts';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Rādīt maršruta priekšskatījumu';
}
