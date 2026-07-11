// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Slovenian (`sl`).
class AppLocalizationsSl extends AppLocalizations {
  AppLocalizationsSl([String locale = 'sl']) : super(locale);

  @override
  String get searchHint => 'Kam želite iti?';

  @override
  String get calculatingRoute => 'Izračunavanje poti…';

  @override
  String get routeNotFoundTitle => 'Pot ni bila najdena';

  @override
  String get noRouteFound => 'Pot ni bila najdena. Preverite povezavo.';

  @override
  String get emptyServerResponse =>
      'Prazen odgovor strežnika. Preverite povezavo.';

  @override
  String routeError(String error) {
    return 'Napaka pri izračunu poti: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS ni na voljo — Settings → App → Roadstr → Permissions → Location';

  @override
  String get acquiringGps => 'Pridobivanje GPS-a…';

  @override
  String get graphhopperServerNotConfigured =>
      'Strežnik GraphHopper ni konfiguriran — uporabljam OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'API-ključ GraphHopper ni konfiguriran — uporabljam OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'API-ključ OpenRouteService ni konfiguriran — uporabljam OSRM';

  @override
  String get chooseRoute => 'Izberite pot';

  @override
  String routeOptionsCount(int count) {
    return '$count možnosti';
  }

  @override
  String get cancel => 'Prekliči';

  @override
  String get startNavigation => 'Začni navigacijo';

  @override
  String get fastestRoute => 'Najhitrejša';

  @override
  String get now => 'Zdaj';

  @override
  String get history => 'Zgodovina';

  @override
  String get clearHistory => 'Počisti';

  @override
  String get selectedPosition => 'Izbrana lokacija';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Meni';

  @override
  String get settingsTitle => 'Nastavitve';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Zemljevid';

  @override
  String get sectionInfo => 'Informacije';

  @override
  String get sectionLanguage => 'Jezik';

  @override
  String get themeLightNostr => 'Svetla · Nostr Vijolična';

  @override
  String get themeLightBitcoin => 'Svetla · Bitcoin Oranžna';

  @override
  String get themeDarkNostr => 'Temna · Nostr Vijolična';

  @override
  String get themeDarkBitcoin => 'Temna · Bitcoin Oranžna';

  @override
  String get langSystem => 'Sistemska privzeta';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Ohrani zaslon prižgan';

  @override
  String get keepScreenOnDescription => 'Prepreči spanje med navigacijo';

  @override
  String get rotateMap => 'Zemljevid sledi smeri';

  @override
  String get rotateMapDescription => 'Zemljevid se zavrti glede na smer vožnje';

  @override
  String get mapTileUrlLabel => 'URL ploščice zemljevida';

  @override
  String get routingProviderLabel => 'Ponudnik usmerjanja';

  @override
  String get osrmProvider => 'OSRM (javni, ključ ni potreben)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokalni/zasebni)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API-ključ)';

  @override
  String get openrouteProvider => 'OpenRouteService (API-ključ)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'API-ključ GraphHopper (neobvezno)';

  @override
  String get verify => 'Preveri';

  @override
  String get graphhopperServerUrlRequired =>
      'Vnesite URL strežnika pred preverjanjem.';

  @override
  String get successTitle => 'Uspešno';

  @override
  String get graphhopperServerReachable => 'Strežnik GraphHopper je dosegljiv';

  @override
  String get errorTitle => 'Napaka';

  @override
  String get close => 'Zapri';

  @override
  String get infoVersion => 'Različica';

  @override
  String get infoProtocol => 'Protokol';

  @override
  String get infoMaps => 'Zemljevidi';

  @override
  String get infoRouting => 'Usmerjanje';

  @override
  String get infoSource => 'Vir';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (samogostovan)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (oblak)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Nepovezano';

  @override
  String get loginWithNostrTitle => 'PRIJAVA Z NOSTROM';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Zasebni ključ nikoli ne zapusti vaše naprave · Priporočeno';

  @override
  String get nsecLoginOption => 'Vnesi nsec';

  @override
  String get nsecLoginDescription =>
      'Zasebni ključ shranjen lokalno · Manj varno';

  @override
  String get connectedViaAmber => 'Povezano prek Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Povezano prek nsec';

  @override
  String get publicKeyLabel => 'JAVNI KLJUČ';

  @override
  String get npubCopiedToClipboard => 'npub kopiran v odložišče';

  @override
  String get logoutTitle => 'Prekini povezavo';

  @override
  String get logoutConfirmation => 'Odstraniti poverilnice Nostr s te naprave?';

  @override
  String get logoutButton => 'Prekini povezavo';

  @override
  String get nostrIdentityInfo =>
      'Z identiteto Nostr lahko na decentraliziran način objavljate prometna opozorila, poročila in točke zanimanja v omrežju Nostr brez centralnih strežnikov.';

  @override
  String get warningTitle => 'Opozorilo';

  @override
  String get nsecWarning =>
      'Tik pred tem ste, da kar tako vtisnete vaš zasebni ključ Nostr neposredno v aplikacijo. Kdorkoli z fizičnim ali oddaljenim dostopom do vaše naprave ga lahko prebere in za vedno prevzame nadzor nad vašo identiteto Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  Varna metoda je Amber (NIP-55): nsec nikoli ne zapusti trezorja za podpisovanje aplikacij.';

  @override
  String get nsecRiskAcknowledgment => 'Razumem tveganje in želim nadaljevati';

  @override
  String get continueButton => 'Nadaljuj';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber je podpisnik aplikacij za Android, skladen z NIP-55. Vaš zasebni ključ ostane izoliran znotraj Amber in nikoli ni deljen.';

  @override
  String get requestKeyFromAmber => 'Zahtevaj javni ključ iz Amber';

  @override
  String get amberNotFound =>
      'Amber ni najden. Namestite ga iz Play Store ali ročno vnesite npub.';

  @override
  String get waitingForAmberResponse => 'Čakanje na odgovor Amber…';

  @override
  String get pasteNpubManually => 'Prilepite npub ročno:';

  @override
  String get confirmNpub => 'Potrdi npub';

  @override
  String get enterNsecTitle => 'Vnesi nsec';

  @override
  String get loginButton => 'Prijava';

  @override
  String get invalidNpubTitle => 'Neveljaven npub';

  @override
  String get invalidNsecTitle => 'Neveljaven nsec';

  @override
  String get invalidNpubMessage =>
      'Prepričajte se, da ste prilepili pravilen npub.';

  @override
  String get invalidNsecMessage =>
      'Prepričajte se, da ste prilepili pravilen nsec.';

  @override
  String get amberResponseError => 'Napaka odgovora Amber';

  @override
  String get ok => 'V redu';

  @override
  String get or => 'ali';

  @override
  String get gpsNotActiveTitle => 'GPS ni aktiven';

  @override
  String get gpsDisabledMessage =>
      'Aktivirajte GPS v nastavitvah naprave, da pridobite svojo lokacijo in uporabljate navigacijo.';

  @override
  String get openSettings => 'Nastavitve';

  @override
  String get myLocation => 'Moja lokacija';

  @override
  String get loginToReport =>
      'Prijavite se z Nostrom (razdelek Profil) za poročanje o dogodkih';

  @override
  String get navigateHere => 'Navigiraj sem';

  @override
  String get reportEventHere => 'Poročaj o dogodku tukaj';

  @override
  String get zapSendSats => 'Zap ⚡ (pošlji sats)';

  @override
  String get sendZap => 'Pošlji Zap';

  @override
  String get chooseAmountSats => 'Izberite znesek v satoshijih (sats):';

  @override
  String get customAmount => 'Poljuben znesek…';

  @override
  String get zapSending => 'Pošiljanje…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Pridobivanje Lightning naslova…';

  @override
  String get noLightningAddress => 'Ta poročevalec nima Lightning naslova';

  @override
  String get requestingInvoice => 'Zahtevanje računa…';

  @override
  String get lnurlUnavailable => 'LNURL ni na voljo';

  @override
  String get invoiceFailed => 'Računa ni mogoče ustvariti';

  @override
  String get openingWallet => 'Odpiranje denarnice…';

  @override
  String get payingViaNwc => 'Plačevanje prek NWC…';

  @override
  String get noLightningWallet => 'Lightning denarnica ni najdena';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats poslano!';
  }

  @override
  String get stillThere => 'Še vedno tam';

  @override
  String get notThereAnymore => 'Ni več tam';

  @override
  String get loginToConfirm =>
      'Prijavite se z Nostrom za potrditev ali izpodbijanje';

  @override
  String get reportAnEvent => 'Poročaj o dogodku';

  @override
  String get optionalComment => 'Neobvezni komentar…';

  @override
  String get publish => 'Objavi';

  @override
  String get publishing => 'Objavljanje…';

  @override
  String get reportPublished => 'Poročilo objavljeno ✓';

  @override
  String get myReports => 'MOJA POROČILA';

  @override
  String get noReportsYet => 'Ni objavljenih poročil';

  @override
  String get zapBalance => 'Zap stanje';

  @override
  String get satoshiFromReports => 'Satoshi, prejeti iz vaših poročil';

  @override
  String get reputationHigh => 'Visoka';

  @override
  String get reputationMedium => 'Srednja';

  @override
  String get reputationLow => 'Nizka';

  @override
  String reputationLabel(String level) {
    return 'Ugled $level';
  }

  @override
  String reliability(int pct) {
    return 'Zanesljivost: $pct%';
  }

  @override
  String get confirmedLabel => 'Potrjeno';

  @override
  String get removedLabel => 'Odstranjeno';

  @override
  String get positionLabel => 'Položaj';

  @override
  String get loadingLabel => 'Nalaganje…';

  @override
  String get sectionWebSearch => 'Spletno iskanje';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Prilepite vaš NWC URI (Alby Hub, Mutiny, Cashu…) za plačevanje Zapsov neposredno iz aplikacije.';

  @override
  String get searchEngineQwantDesc =>
      'Evropski, zasebnost na prvem mestu. Brez sledenja, brez oglaševalskih profilov. Priporočeno.';

  @override
  String get searchEngineBraveDesc =>
      'Neodvisni indeks, odprtokoden. Brez odvisnosti od Googla ali Binga. Nič profiliranja.';

  @override
  String get searchEngineDdgDesc =>
      'Osredotočen na zasebnost in priljubljen. Rezultati delno iz Binga — imejte to v mislih.';

  @override
  String get searchEngineStartpageDesc =>
      'Googlov rezultati brez predaje vaših podatkov Googlu. Razumni kompromis.';

  @override
  String get searchEngineGoogleDesc =>
      'Zelo učinkovito. A zapomnite si: Google vas pozna bolje kot vaša mama in vse prodaja oglaševalcem. Vaša odločitev. 🍪';

  @override
  String get categoryPolice => 'Policija';

  @override
  String get categorySpeedCamera => 'Radar za merjenje hitrosti';

  @override
  String get categoryTrafficJam => 'Prometni zamašek';

  @override
  String get categoryAccident => 'Prometna nesreča';

  @override
  String get categoryRoadClosure => 'Zapora ceste';

  @override
  String get categoryConstruction => 'Gradbena dela';

  @override
  String get categoryHazard => 'Nevarnost';

  @override
  String get categoryRoadCondition => 'Stanje ceste';

  @override
  String get categoryPothole => 'Udarna jama';

  @override
  String get categoryFog => 'Megla';

  @override
  String get categoryIce => 'Led';

  @override
  String get categoryAnimal => 'Žival';

  @override
  String get categoryOther => 'Drugo';

  @override
  String get dateTimeLabel => 'Datum / čas';

  @override
  String minutesAgo(int count) {
    return 'pred $count min';
  }

  @override
  String hoursAgo(int count) {
    return 'pred ${count}h';
  }

  @override
  String daysAgo(int count) {
    return 'pred ${count}d';
  }

  @override
  String get sectionFavorites => 'Shranjene lokacije';

  @override
  String get addFavorite => 'Dodaj lokacijo';

  @override
  String get favoriteLabelHint => 'Ime (npr. Dom, Pisarna)';

  @override
  String get favoriteAddressHint => 'Naslov';

  @override
  String get favoriteGeocodingError =>
      'Naslov ni bil najden. Poskusi z natančnejšim naslovom.';

  @override
  String get trafficAlertTitle => 'Nov promet na poti';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category prijavljeno $age na vaši poti.\n\nAli želite poiskati alternativno pot?';
  }

  @override
  String get trafficContinue => 'Nadaljuj';

  @override
  String get trafficRecalculate => 'Preračunaj pot';

  @override
  String get navExitTitle => 'Izhod iz navigacije?';

  @override
  String get navExitBody =>
      'Ali želite ustaviti navigacijo in se vrniti na zemljevid?';

  @override
  String get navContinue => 'Nadaljuj navigacijo';

  @override
  String get navExit => 'Da, izhod';

  @override
  String get loadingInfo => 'Nalaganje informacij…';

  @override
  String get conditionsOnRoute => 'Pogoji na poti';

  @override
  String get calculateRoute => 'Izračunaj pot';

  @override
  String get sectionNavigationVoice => 'Glas navigacije';

  @override
  String get voiceGuidance => 'Glasovno vodenje';

  @override
  String get voiceGuidanceDesc =>
      'Med navigacijo na glas preberi navodila za zavijanje';

  @override
  String get testVoiceEngine => 'Preizkusi glasovni mehanizem';

  @override
  String get testVoiceEngineDesc =>
      'Dotaknite se za preverjanje mehanizma TTS in navodila za namestitev';

  @override
  String get ttsDialogTitle => 'Manjka glasovni mehanizem';

  @override
  String get ttsDialogBody =>
      'Ni bilo mogoče najti delujočega mehanizma Text-to-Speech.\n\nRoadstr se zanaša izključno na odprtokodno programsko opremo — namestite enega od teh brezplačnih mehanizmov iz F-Droid:';

  @override
  String get ttsRhvoiceDesc => 'Naravno zveneč glas, omejen seznam jezikov';

  @override
  String get ttsEspeakDesc => 'Pokriva več kot 100 jezikov, robotski glas';

  @override
  String get ttsInstallNote =>
      '⚠️ Po namestitvi:\n1. Android nastavitve → Dostopnost → Pretvorba besedila v govor\n2. Izberite pravkar nameščeni mehanizem\n3. Prenesite glasovne podatke za svoj jezik\n4. Popolnoma znova zaženite Roadstr';

  @override
  String get ttsTestNow => 'Preizkusi zdaj';

  @override
  String get voiceUnsupportedTitle => 'Glasovno vodenje ni na voljo';

  @override
  String get voiceUnsupportedBody =>
      'Vaš jezik še ni podprt za govorjena navodila za zavijanje. Navigacijska navodila se bodo še naprej prikazovala kot besedilo na zaslonu.';

  @override
  String get kokoroModelTitle => 'Glasovni model (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Ni preneseno · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Prenos...';

  @override
  String get kokoroModelStatusReady => 'Glasovni model je pripravljen';

  @override
  String get kokoroModelDownloadBtn => 'Prenesi';

  @override
  String get kokoroModelSupportedLangs =>
      'Podpira: italijanščina, angleščina, španščina, francoščina, japonščina, kitajščina, portugalščina';

  @override
  String get autoDarkMode => 'Samodejno temno ozadje';

  @override
  String get autoDarkModeDesc => 'Aktivira temno temo ob sončnem zahodu';

  @override
  String get settingsImperialUnits => 'Imperialne enote';

  @override
  String get settingsImperialUnitsDesc =>
      'Milje in čevlji namesto kilometrov in metrov';

  @override
  String get arrivedTitle => '🎉 Ste prispeli!';

  @override
  String get arrivedBody => 'Dosegli ste cilj.';

  @override
  String get arrivedFeedbackPrompt => 'Kako je šlo?';

  @override
  String get feedbackBad => 'Slabo';

  @override
  String get feedbackGood => 'Dobro!';

  @override
  String get feedbackDialogTitle => 'Povejte nam, kaj je šlo narobe';

  @override
  String get feedbackHint => 'Opišite težavo…';

  @override
  String get feedbackSent => 'Povratne informacije poslane — hvala! 🙏';

  @override
  String get feedbackSubmit => 'Pošlji';

  @override
  String get transportModeCar => 'Avto';

  @override
  String get transportModeWalk => 'Peš';

  @override
  String etaArrivalLabel(String time) {
    return 'Prih. $time';
  }

  @override
  String get supportRoadstr => 'Podprite Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address kopirano v odložišče';
  }

  @override
  String get disclaimerTitle => 'Pomembno obvestilo';

  @override
  String get disclaimerAccept => 'Prebral/a sem in sprejemam';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Beri na Wikipediji';

  @override
  String searchOnEngine(String engine) {
    return 'Iskanje na $engine';
  }

  @override
  String get plannerFromHint => 'Od…';

  @override
  String get plannerToHint => 'Cilj…';

  @override
  String departEta(String dep, String arr) {
    return 'Odhod $dep  →  Prihod $arr';
  }

  @override
  String get modeCar => 'Avto';

  @override
  String get modeBike => 'Kolo';

  @override
  String get modeWalk => 'Peš';

  @override
  String windSpeed(String speed) {
    return 'veter $speed km/h';
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
  String get weatherClear => 'Jasno';

  @override
  String get weatherPartlyCloudy => 'Spremenljivo';

  @override
  String get weatherCloudy => 'Oblačno';

  @override
  String get weatherFog => 'Megla';

  @override
  String get weatherLightRain => 'Rahel dež';

  @override
  String get weatherRain => 'Dež';

  @override
  String get weatherSnow => 'Sneg';

  @override
  String get weatherShowers => 'Nalivi';

  @override
  String get weatherThunderstorm => 'Nevihta';

  @override
  String get ztlAheadWarning => 'ZTL cona naprej — pot vstopa v omejeno cono';

  @override
  String get ztlInsideWarning => 'Omejena prometna cona';

  @override
  String get onboardingAppSubtitle => 'Odprtokodna Nostr navigacija';

  @override
  String get onboardingWelcomeTitle => 'Dobrodošli';

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
  String get onboardingGetStarted => 'Začni';

  @override
  String get onboardingNostrTitle => 'Nostr identiteta';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Povezano';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (priporočeno)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'Ključ nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Neveljaven ključ nsec — preverite in poskusite znova.';

  @override
  String get onboardingSkip => 'Preskoči za zdaj';

  @override
  String get onboardingContinue => 'Nadaljuj';

  @override
  String get onboardingEnterNsec => 'Vnesite ključ nsec';

  @override
  String get onboardingSetupTitle => 'Nastavi Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Lokacija';

  @override
  String get onboardingLocationGranted => 'Dostop do lokacije odobren';

  @override
  String get onboardingLocationRequired => 'Potrebno za GPS navigacijo';

  @override
  String get onboardingGrantButton => 'Dovoli';

  @override
  String get onboardingGrapheneTitle => 'Uporabniki GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'AI glasovno vodenje (neobvezno)';

  @override
  String get onboardingVoiceReady => 'Glasovni model Kokoro je pripravljen';

  @override
  String get onboardingVoiceDownloading => 'Prenos glasovnega modela…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Prenesi';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Pripravljeni ste na navigacijo!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Gremo!';

  @override
  String get onboardingProfileLoading => 'Nalaganje profila…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get onboardingVpnNotice =>
      'Za največjo zasebnost — prijave se širijo po omrežju Nostr — je priporočljiva uporaba VPN-ja. Priporočena izbira je Mullvad, ki ga je mogoče plačati z Bitcoinom.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Za zanesljivo delovanje nastavite dovoljenje za lokacijo na »Vedno dovoli«, ne le med uporabo aplikacije.';
}
