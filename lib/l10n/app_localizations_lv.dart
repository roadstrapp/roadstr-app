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
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Lasīt Vikipēdijā';

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
}
