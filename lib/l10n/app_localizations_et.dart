// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Estonian (`et`).
class AppLocalizationsEt extends AppLocalizations {
  AppLocalizationsEt([String locale = 'et']) : super(locale);

  @override
  String get searchHint => 'Kuhu soovite minna?';

  @override
  String get calculatingRoute => 'Marsruudi arvutamine…';

  @override
  String get routeNotFoundTitle => 'Marsruuti ei leitud';

  @override
  String get noRouteFound => 'Marsruuti ei leitud. Kontrollige ühendust.';

  @override
  String get emptyServerResponse =>
      'Tühi serveri vastus. Kontrollige ühendust.';

  @override
  String routeError(String error) {
    return 'Marsruudi arvutusviga: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS pole saadaval — Settings → App → Roadstr → Permissions → Location';

  @override
  String get acquiringGps => 'GPS-i hankimine…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopperi server pole seadistatud — kasutatakse OSRM-i';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopperi API-võti pole seadistatud — kasutatakse OSRM-i';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService\'i API-võti pole seadistatud — kasutatakse OSRM-i';

  @override
  String get chooseRoute => 'Vali marsruut';

  @override
  String routeOptionsCount(int count) {
    return '$count valikut';
  }

  @override
  String get cancel => 'Tühista';

  @override
  String get startNavigation => 'Alusta navigeerimist';

  @override
  String get fastestRoute => 'Kiirem';

  @override
  String get now => 'Praegu';

  @override
  String get history => 'Ajalugu';

  @override
  String get clearHistory => 'Tühjenda';

  @override
  String get selectedPosition => 'Valitud asukoht';

  @override
  String get bottomBarProfile => 'Profiil';

  @override
  String get bottomBarMenu => 'Menüü';

  @override
  String get settingsTitle => 'Seaded';

  @override
  String get sectionTheme => 'Teema';

  @override
  String get sectionMap => 'Kaart';

  @override
  String get sectionInfo => 'Teave';

  @override
  String get sectionLanguage => 'Keel';

  @override
  String get themeLightNostr => 'Hele · Nostr Violetne';

  @override
  String get themeLightBitcoin => 'Hele · Bitcoin Oranž';

  @override
  String get themeDarkNostr => 'Tume · Nostr Violetne';

  @override
  String get themeDarkBitcoin => 'Tume · Bitcoin Oranž';

  @override
  String get langSystem => 'Süsteemi vaikeväärtus';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Hoia ekraan üleval';

  @override
  String get keepScreenOnDescription =>
      'Takistab une-olekut navigeerimise ajal';

  @override
  String get rotateMap => 'Kaart järgib suunda';

  @override
  String get rotateMapDescription => 'Kaart pöörub sõidusuuna järgi';

  @override
  String get mapTileUrlLabel => 'Kaardi tile\'i URL';

  @override
  String get routingProviderLabel => 'Marsruutimise pakkuja';

  @override
  String get osrmProvider => 'OSRM (avalik, võtit pole vaja)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (kohalik/privaatne)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API-võti)';

  @override
  String get openrouteProvider => 'OpenRouteService (API-võti)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopperi API-võti (valikuline)';

  @override
  String get verify => 'Kinnita';

  @override
  String get graphhopperServerUrlRequired =>
      'Sisestage serveri URL enne kinnitamist.';

  @override
  String get successTitle => 'Õnnestus';

  @override
  String get graphhopperServerReachable => 'GraphHopperi server on kättesaadav';

  @override
  String get errorTitle => 'Viga';

  @override
  String get close => 'Sulge';

  @override
  String get infoVersion => 'Versioon';

  @override
  String get infoProtocol => 'Protokoll';

  @override
  String get infoMaps => 'Kaardid';

  @override
  String get infoRouting => 'Marsruutimine';

  @override
  String get infoSource => 'Allikas';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (ise majutatud)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (pilv)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profiil';

  @override
  String get notConnected => 'Pole ühendatud';

  @override
  String get loginWithNostrTitle => 'LOGI SISSE NOSTRIGA';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Privaatvõti ei lahku kunagi seadmest · Soovitatav';

  @override
  String get nsecLoginOption => 'Sisesta nsec';

  @override
  String get nsecLoginDescription =>
      'Privaatvõti salvestatakse kohapeal · Vähem turvaline';

  @override
  String get connectedViaAmber => 'Ühendatud Amberi kaudu (NIP-55)';

  @override
  String get connectedViaNsec => 'Ühendatud nsec kaudu';

  @override
  String get publicKeyLabel => 'AVALIK VÕTI';

  @override
  String get npubCopiedToClipboard => 'npub kopeeriti lõikelauale';

  @override
  String get logoutTitle => 'Katkesta ühendus';

  @override
  String get logoutConfirmation =>
      'Eemaldada Nostri mandaadid sellest seadmest?';

  @override
  String get logoutButton => 'Katkesta ühendus';

  @override
  String get nostrIdentityInfo =>
      'Nostri identiteediga saate avaldada liiklushoiatusi, aruandeid ja huvipunkte detsentraliseeritud viisil Nostri võrgus ilma kesksete serveriteta.';

  @override
  String get warningTitle => 'Hoiatus';

  @override
  String get nsecWarning =>
      'Olete sellest lahkumas, et visata oma Nostri privaatvõti otse rakendusse. Igaüks, kellel on füüsiline või kaugjuurdepääs teie seadmele, saab selle lugeda ja teie Nostri identiteedi üle kontrolli jäädavalt üle võtta.';

  @override
  String get amberSecureMethodHint =>
      '✦  Turvaline meetod on Amber (NIP-55): nsec ei lahku kunagi rakenduse allkirjastamishoidlast.';

  @override
  String get nsecRiskAcknowledgment => 'Mõistan riski ja soovin siiski jätkata';

  @override
  String get continueButton => 'Jätka';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber on NIP-55 nõuetele vastav Androidi rakenduse allkirjastaja. Teie privaatvõti jääb Amberi sisse isoleerituks ega ole kunagi jagatud.';

  @override
  String get requestKeyFromAmber => 'Taotle avalikku võtit Amberilt';

  @override
  String get amberNotFound =>
      'Amberi ei leitud. Installige see Play Store\'ist või sisestage oma npub käsitsi.';

  @override
  String get waitingForAmberResponse => 'Ootan Amberi vastust…';

  @override
  String get pasteNpubManually => 'Kleepige oma npub käsitsi:';

  @override
  String get confirmNpub => 'Kinnita npub';

  @override
  String get enterNsecTitle => 'Sisesta nsec';

  @override
  String get loginButton => 'Logi sisse';

  @override
  String get invalidNpubTitle => 'Kehtetu npub';

  @override
  String get invalidNsecTitle => 'Kehtetu nsec';

  @override
  String get invalidNpubMessage => 'Veenduge, et kleepisite õige npub.';

  @override
  String get invalidNsecMessage => 'Veenduge, et kleepisite õige nsec.';

  @override
  String get amberResponseError => 'Amberi vastuse viga';

  @override
  String get ok => 'OK';

  @override
  String get or => 'või';

  @override
  String get gpsNotActiveTitle => 'GPS pole aktiivne';

  @override
  String get gpsDisabledMessage =>
      'Aktiveerige GPS seadme seadetes, et saada oma asukoht ja kasutada navigeerimist.';

  @override
  String get openSettings => 'Seaded';

  @override
  String get myLocation => 'Minu asukoht';

  @override
  String get loginToReport =>
      'Sündmustest teatamiseks logige sisse Nostriga (Profiili jaotis)';

  @override
  String get navigateHere => 'Navigeeri siia';

  @override
  String get reportEventHere => 'Teata sündmusest siin';

  @override
  String get zapSendSats => 'Zap ⚡ (saada sats-e)';

  @override
  String get sendZap => 'Saada Zap';

  @override
  String get chooseAmountSats => 'Valige summa satoshides (sats):';

  @override
  String get customAmount => 'Kohandatud summa…';

  @override
  String get zapSending => 'Saatmine…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Lightningi aadressi hankimine…';

  @override
  String get noLightningAddress => 'Sellel reporteril pole Lightningi aadressi';

  @override
  String get requestingInvoice => 'Arve taotlemine…';

  @override
  String get lnurlUnavailable => 'LNURL pole saadaval';

  @override
  String get invoiceFailed => 'Arvet ei saa genereerida';

  @override
  String get openingWallet => 'Rahakoti avamine…';

  @override
  String get payingViaNwc => 'Maksmine NWC kaudu…';

  @override
  String get noLightningWallet => 'Lightningi rahakotti ei leitud';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats saadetud!';
  }

  @override
  String get stillThere => 'Endiselt seal';

  @override
  String get notThereAnymore => 'Enam pole seal';

  @override
  String get loginToConfirm =>
      'Kinnitamiseks või vaidlustamiseks logige sisse Nostriga';

  @override
  String get reportAnEvent => 'Teata sündmusest';

  @override
  String get optionalComment => 'Valikuline kommentaar…';

  @override
  String get publish => 'Avalda';

  @override
  String get publishing => 'Avaldamine…';

  @override
  String get reportPublished => 'Aruanne avaldatud ✓';

  @override
  String get myReports => 'MINU ARUANDED';

  @override
  String get noReportsYet => 'Avaldatud aruandeid pole';

  @override
  String get zapBalance => 'Zap-saldo';

  @override
  String get satoshiFromReports => 'Teie aruannetest saadud satoshi';

  @override
  String get reputationHigh => 'Kõrge';

  @override
  String get reputationMedium => 'Keskmine';

  @override
  String get reputationLow => 'Madal';

  @override
  String reputationLabel(String level) {
    return 'Maine $level';
  }

  @override
  String reliability(int pct) {
    return 'Usaldusväärsus: $pct%';
  }

  @override
  String get confirmedLabel => 'Kinnitatud';

  @override
  String get removedLabel => 'Eemaldatud';

  @override
  String get positionLabel => 'Asukoht';

  @override
  String get loadingLabel => 'Laadimine…';

  @override
  String get sectionWebSearch => 'Veebiotsing';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Kleepige oma NWC URI (Alby Hub, Mutiny, Cashu…), et maksta Zaps-e otse rakendusest.';

  @override
  String get searchEngineQwantDesc =>
      'Euroopa, privaatsus esmalt. Jälgimiseta, reklaamiprofiilita. Soovitatav.';

  @override
  String get searchEngineBraveDesc =>
      'Sõltumatu indeks, avatud lähtekood. Google\'ist ega Bingist sõltuvuseta. Nullprofiiliga.';

  @override
  String get searchEngineDdgDesc =>
      'Privaatsusele orienteeritud ja populaarne. Tulemused osaliselt Bingist — pidage seda meeles.';

  @override
  String get searchEngineStartpageDesc =>
      'Google\'i tulemused ilma oma andmeid Google\'ile andmata. Mõistlik kompromiss.';

  @override
  String get searchEngineGoogleDesc =>
      'Väga tõhus. Aga pidage meeles: Google teab teid paremini kui teie ema ja müüb kõike reklaamijatele. Teie otsus muidugi. 🍪';

  @override
  String get categoryPolice => 'Politsei';

  @override
  String get categorySpeedCamera => 'Kiiruskaamerna';

  @override
  String get categoryTrafficJam => 'Liiklusummik';

  @override
  String get categoryAccident => 'Õnnetus';

  @override
  String get categoryRoadClosure => 'Tee sulgemine';

  @override
  String get categoryConstruction => 'Ehitustööd';

  @override
  String get categoryHazard => 'Oht';

  @override
  String get categoryRoadCondition => 'Teeolude seisund';

  @override
  String get categoryPothole => 'Auk teel';

  @override
  String get categoryFog => 'Udu';

  @override
  String get categoryIce => 'Jää';

  @override
  String get categoryAnimal => 'Loom';

  @override
  String get categoryOther => 'Muu';

  @override
  String get dateTimeLabel => 'Kuupäev / kellaaeg';

  @override
  String minutesAgo(int count) {
    return '$count min tagasi';
  }

  @override
  String hoursAgo(int count) {
    return '${count}h tagasi';
  }

  @override
  String daysAgo(int count) {
    return '${count}p tagasi';
  }

  @override
  String get sectionFavorites => 'Salvestatud kohad';

  @override
  String get addFavorite => 'Lisa koht';

  @override
  String get favoriteLabelHint => 'Nimi (nt Kodu, Kontor)';

  @override
  String get favoriteAddressHint => 'Aadress';

  @override
  String get favoriteGeocodingError =>
      'Aadressi ei leitud. Proovi täpsemat aadressi.';

  @override
  String get trafficAlertTitle => 'Uus liiklus marsruudil';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category teatati $age teie marsruudil.\n\nKas soovite leida alternatiivset marsruuti?';
  }

  @override
  String get trafficContinue => 'Jätka';

  @override
  String get trafficRecalculate => 'Arvuta marsruut uuesti';

  @override
  String get navExitTitle => 'Lõpeta navigeerimine?';

  @override
  String get navExitBody =>
      'Kas soovite peatada navigeerimise ja naasta kaardile?';

  @override
  String get navContinue => 'Jätka navigeerimist';

  @override
  String get navExit => 'Jah, välju';

  @override
  String get loadingInfo => 'Laadin teavet…';

  @override
  String get conditionsOnRoute => 'Tingimused marsruudil';

  @override
  String get calculateRoute => 'Arvuta marsruut';

  @override
  String get sectionNavigationVoice => 'Navigeerimishääl';

  @override
  String get voiceGuidance => 'Häälejuhendamine';

  @override
  String get voiceGuidanceDesc =>
      'Loe pööramisjuhiseid navigeerimise ajal valjult ette';

  @override
  String get testVoiceEngine => 'Testi häälemootorit';

  @override
  String get testVoiceEngineDesc =>
      'Puuduta, et kontrollida TTS-mootorit ja saada seadistusjuhised';

  @override
  String get ttsDialogTitle => 'Häälemootor puudub';

  @override
  String get ttsDialogBody =>
      'Töötavat Text-to-Speech mootorit ei leitud.\n\nRoadstr tugineb ainult avatud lähtekoodiga tarkvarale — installi üks neist tasuta mootoritest F-Droidist:';

  @override
  String get ttsRhvoiceDesc => 'Loomulik hääl, piiratud keelte loend';

  @override
  String get ttsEspeakDesc => 'Katab üle 100 keele, robotlik hääl';

  @override
  String get ttsInstallNote =>
      '⚠️ Pärast installimist:\n1. Androidi seaded → Hõlbustus → Tekst kõneks\n2. Vali äsja installitud mootor\n3. Laadi alla oma keele häälandmed\n4. Taaskäivita Roadstr täielikult';

  @override
  String get ttsTestNow => 'Testi kohe';

  @override
  String get voiceUnsupportedTitle => 'Häälejuhendamine pole saadaval';

  @override
  String get voiceUnsupportedBody =>
      'Sinu keel ei ole veel toetatud häälega pööramisjuhiste jaoks. Navigeerimisjuhised kuvatakse jätkuvalt tekstina ekraanil.';

  @override
  String get kokoroModelTitle => 'Häälemudel (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Laadimata · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Allalaadimine...';

  @override
  String get kokoroModelStatusReady => 'Häälemudel on valmis';

  @override
  String get kokoroModelDownloadBtn => 'Laadi alla';

  @override
  String get kokoroModelSupportedLangs =>
      'Toetab: itaalia, inglise, hispaania, prantsuse, jaapani, hiina, portugali';

  @override
  String get autoDarkMode => 'Automaatne tume teema';

  @override
  String get autoDarkModeDesc => 'Aktiveerib tumeda teema päikeseloojangul';
}
