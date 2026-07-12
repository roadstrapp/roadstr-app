// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Hungarian (`hu`).
class AppLocalizationsHu extends AppLocalizations {
  AppLocalizationsHu([String locale = 'hu']) : super(locale);

  @override
  String get searchHint => 'Hová szeretne menni?';

  @override
  String get calculatingRoute => 'Útvonal számítása…';

  @override
  String get routeNotFoundTitle => 'Útvonal nem található';

  @override
  String get noRouteFound =>
      'Nem található útvonal. Ellenőrizze a kapcsolatot.';

  @override
  String get emptyServerResponse =>
      'Üres szerverhválasz. Ellenőrizze a kapcsolatot.';

  @override
  String routeError(String error) {
    return 'Útvonalszámítási hiba: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS nem érhető el — Beállítások → Alkalmazás → Roadstr → Engedélyek → Hely';

  @override
  String get acquiringGps => 'GPS lekérése…';

  @override
  String get graphhopperServerNotConfigured =>
      'A GraphHopper szerver nincs beállítva — OSRM használata';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'A GraphHopper API kulcs nincs beállítva — OSRM használata';

  @override
  String get openrouteApiKeyNotConfigured =>
      'Az OpenRouteService API kulcs nincs beállítva — OSRM használata';

  @override
  String get chooseRoute => 'Útvonal választása';

  @override
  String routeOptionsCount(int count) {
    return '$count lehetőség';
  }

  @override
  String get cancel => 'Mégse';

  @override
  String get startNavigation => 'Navigáció indítása';

  @override
  String get fastestRoute => 'Leggyorsabb';

  @override
  String get now => 'Most';

  @override
  String get history => 'Előzmények';

  @override
  String get clearHistory => 'Törlés';

  @override
  String get selectedPosition => 'Kiválasztott pozíció';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Menü';

  @override
  String get settingsTitle => 'Beállítások';

  @override
  String get sectionTheme => 'Téma';

  @override
  String get sectionMap => 'Térkép';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Nyelv';

  @override
  String get themeLightNostr => 'Világos · Nostr Lila';

  @override
  String get themeLightBitcoin => 'Világos · Bitcoin Narancs';

  @override
  String get themeDarkNostr => 'Sötét · Nostr Lila';

  @override
  String get themeDarkBitcoin => 'Sötét · Bitcoin Narancs';

  @override
  String get langSystem => 'Rendszer alapértelmezett';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Képernyő bekapcsolt állapotban tartása';

  @override
  String get keepScreenOnDescription =>
      'Megakadályozza az alvó módot navigáció közben';

  @override
  String get rotateMap => 'A térkép követi az irányt';

  @override
  String get rotateMapDescription => 'A térkép a haladási irány alapján forog';

  @override
  String get mapTileUrlLabel => 'Térképcsempe URL';

  @override
  String get routingProviderLabel => 'Útválasztó szolgáltató';

  @override
  String get osrmProvider => 'OSRM (nyilvános, kulcs nem szükséges)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (helyi/privát)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API kulcs)';

  @override
  String get openrouteProvider => 'OpenRouteService (API kulcs)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API kulcs (opcionális)';

  @override
  String get verify => 'Ellenőrzés';

  @override
  String get graphhopperServerUrlRequired =>
      'Adja meg a szerver URL-t az ellenőrzés előtt.';

  @override
  String get successTitle => 'Siker';

  @override
  String get graphhopperServerReachable => 'A GraphHopper szerver elérhető';

  @override
  String get errorTitle => 'Hiba';

  @override
  String get close => 'Bezárás';

  @override
  String get infoVersion => 'Verzió';

  @override
  String get infoProtocol => 'Protokoll';

  @override
  String get infoMaps => 'Térképek';

  @override
  String get infoRouting => 'Útválasztás';

  @override
  String get infoSource => 'Forrás';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (önhosztolt)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (felhő)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Nem csatlakoztatva';

  @override
  String get loginWithNostrTitle => 'BEJELENTKEZÉS NOSTR-RAL';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'A privát kulcs soha nem hagyja el az eszközt · Ajánlott';

  @override
  String get nsecLoginOption => 'nsec beillesztése';

  @override
  String get nsecLoginDescription =>
      'Privát kulcs helyi tárolása · Kevésbé biztonságos';

  @override
  String get connectedViaAmber => 'Csatlakozva Amber-en keresztül (NIP-55)';

  @override
  String get connectedViaNsec => 'Csatlakozva nsec-en keresztül';

  @override
  String get publicKeyLabel => 'NYILVÁNOS KULCS';

  @override
  String get npubCopiedToClipboard => 'npub vágólapra másolva';

  @override
  String get logoutTitle => 'Lecsatlakozás';

  @override
  String get logoutConfirmation =>
      'Eltávolítja a Nostr hitelesítő adatokat erről az eszközről?';

  @override
  String get logoutButton => 'Lecsatlakozás';

  @override
  String get nostrIdentityInfo =>
      'Nostr identitással decentralizált módon tehet közzé forgalmi riasztásokat, jelentéseket és érdekességeket a Nostr hálózaton, központi szerverek nélkül.';

  @override
  String get warningTitle => 'Figyelmeztetés';

  @override
  String get nsecWarning =>
      'Az Ön Nostr privát kulcsát közvetlenül egy alkalmazásba készül beírni. Bárki, aki fizikailag vagy távolról hozzáfér az eszközéhez, elolvashatja, és véglegesen átveheti az irányítást Nostr identitása felett.';

  @override
  String get amberSecureMethodHint =>
      '✦  A biztonságos módszer az Amber (NIP-55): az nsec soha nem hagyja el az alkalmazás aláírói széfet.';

  @override
  String get nsecRiskAcknowledgment =>
      'Megértem a kockázatot és mégis folytatni szeretnék';

  @override
  String get continueButton => 'Folytatás';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Az Amber egy NIP-55 kompatibilis Android alkalmazás-aláíró. Privát kulcsa az Amberen belül marad és soha nem kerül megosztásra.';

  @override
  String get requestKeyFromAmber => 'Nyilvános kulcs kérése az Ambertől';

  @override
  String get amberNotFound =>
      'Az Amber nem található. Telepítse a Play Áruházból, vagy adja meg manuálisan az npub-ját.';

  @override
  String get waitingForAmberResponse => 'Várakozás az Amber válaszára…';

  @override
  String get pasteNpubManually => 'Illessze be manuálisan az npub-ját:';

  @override
  String get confirmNpub => 'npub megerősítése';

  @override
  String get enterNsecTitle => 'nsec beillesztése';

  @override
  String get loginButton => 'Bejelentkezés';

  @override
  String get invalidNpubTitle => 'Érvénytelen npub';

  @override
  String get invalidNsecTitle => 'Érvénytelen nsec';

  @override
  String get invalidNpubMessage =>
      'Győződjön meg arról, hogy a megfelelő npub-ot illesztette be.';

  @override
  String get invalidNsecMessage =>
      'Győződjön meg arról, hogy a megfelelő nsec-et illesztette be.';

  @override
  String get amberResponseError => 'Amber válasz hiba';

  @override
  String get ok => 'OK';

  @override
  String get or => 'vagy';

  @override
  String get gpsNotActiveTitle => 'GPS nem aktív';

  @override
  String get gpsDisabledMessage =>
      'Engedélyezze a GPS-t az eszközbeállításokban, hogy megkapja tartózkodási helyét és használja a navigációt.';

  @override
  String get openSettings => 'Beállítások';

  @override
  String get myLocation => 'Saját helyzetm';

  @override
  String get loginToReport =>
      'Jelentkezzen be Nostr-ral (Profil szekció) az események bejelentéséhez';

  @override
  String get navigateHere => 'Ide navigálás';

  @override
  String get reportEventHere => 'Esemény bejelentése itt';

  @override
  String get zapSendSats => 'Zap ⚡ (sats küldése)';

  @override
  String get sendZap => 'Zap küldése';

  @override
  String get chooseAmountSats => 'Válasszon összeget satoshi (sats) egységben:';

  @override
  String get customAmount => 'Egyéni összeg…';

  @override
  String get zapSending => 'Küldés…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Lightning cím lekérése…';

  @override
  String get noLightningAddress => 'Ennek a bejelentőnek nincs Lightning címe';

  @override
  String get requestingInvoice => 'Számla kérése…';

  @override
  String get lnurlUnavailable => 'LNURL nem elérhető';

  @override
  String get invoiceFailed => 'Nem sikerült számlát generálni';

  @override
  String get openingWallet => 'Pénztárca megnyitása…';

  @override
  String get payingViaNwc => 'Fizetés NWC-n keresztül…';

  @override
  String get noLightningWallet => 'Nem található Lightning pénztárca';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats elküldve!';
  }

  @override
  String get stillThere => 'Még ott van';

  @override
  String get notThereAnymore => 'Már nincs ott';

  @override
  String get loginToConfirm =>
      'Jelentkezzen be Nostr-ral a megerősítéshez vagy vitatáshoz';

  @override
  String get reportAnEvent => 'Esemény bejelentése';

  @override
  String get optionalComment => 'Opcionális megjegyzés…';

  @override
  String get publish => 'Közzététel';

  @override
  String get publishing => 'Közzététel…';

  @override
  String get reportPublished => 'Jelentés közzétéve ✓';

  @override
  String get myReports => 'SAJÁT JELENTÉSEIM';

  @override
  String get noReportsYet => 'Nem tett közzé jelentéseket';

  @override
  String get zapBalance => 'Zap egyenleg';

  @override
  String get satoshiFromReports => 'Jelentéseiből kapott satoshi';

  @override
  String get reputationHigh => 'Magas';

  @override
  String get reputationMedium => 'Közepes';

  @override
  String get reputationLow => 'Alacsony';

  @override
  String reputationLabel(String level) {
    return 'Hírnév $level';
  }

  @override
  String reliability(int pct) {
    return 'Megbízhatóság: $pct%';
  }

  @override
  String get confirmedLabel => 'Megerősítve';

  @override
  String get removedLabel => 'Eltávolítva';

  @override
  String get positionLabel => 'Pozíció';

  @override
  String get loadingLabel => 'Betöltés…';

  @override
  String get sectionWebSearch => 'Webkeresés';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Illessze be NWC URI-ját (Alby Hub, Mutiny, Cashu…) a Zapok közvetlen kifizetéséhez az alkalmazásból.';

  @override
  String get searchEngineQwantDesc =>
      'Európai, adatvédelem-első. Nincs nyomon követés, nincsenek hirdetési profilok. Ajánlott.';

  @override
  String get searchEngineBraveDesc =>
      'Független index, nyílt forráskódú. Nincs Google vagy Bing függőség. Nulla profilalkotás.';

  @override
  String get searchEngineDdgDesc =>
      'Adatvédelmi fókuszú és népszerű. Az eredmények részben Bing-ből származnak — ezt érdemes szem előtt tartani.';

  @override
  String get searchEngineStartpageDesc =>
      'Google eredmények anélkül, hogy adatait átadná a Google-nak. Ésszerű kompromisszum.';

  @override
  String get searchEngineGoogleDesc =>
      'Nagyon hatékony. De ne feledje: a Google jobban ismeri önt, mint az anyja, és mindent elad a hirdetőknek. Az ön döntése. 🍪';

  @override
  String get categoryPolice => 'Rendőrség';

  @override
  String get categorySpeedCamera => 'Sebességmérő kamera';

  @override
  String get categoryTrafficJam => 'Dugó';

  @override
  String get categoryAccident => 'Baleset';

  @override
  String get categoryRoadClosure => 'Útlezárás';

  @override
  String get categoryConstruction => 'Útépítés';

  @override
  String get categoryHazard => 'Veszély';

  @override
  String get categoryRoadCondition => 'Útállapot';

  @override
  String get categoryPothole => 'Kátyú';

  @override
  String get categoryFog => 'Köd';

  @override
  String get categoryIce => 'Jég';

  @override
  String get categoryAnimal => 'Állat';

  @override
  String get categoryOther => 'Egyéb';

  @override
  String get dateTimeLabel => 'Dátum / idő';

  @override
  String minutesAgo(int count) {
    return '$count perce';
  }

  @override
  String hoursAgo(int count) {
    return '$count órája';
  }

  @override
  String daysAgo(int count) {
    return '$count napja';
  }

  @override
  String get sectionFavorites => 'Mentett helyek';

  @override
  String get addFavorite => 'Hely hozzáadása';

  @override
  String get favoriteLabelHint => 'Név (pl. Otthon, Iroda)';

  @override
  String get favoriteAddressHint => 'Cím';

  @override
  String get favoriteGeocodingError =>
      'A cím nem található. Próbálj pontosabb címet.';

  @override
  String get trafficAlertTitle => 'Új forgalom az útvonalon';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category jelezve $age az útvonalon.\n\nSzeretne alternatív útvonalat találni?';
  }

  @override
  String get trafficContinue => 'Folytatás';

  @override
  String get trafficRecalculate => 'Útvonal újraszámítása';

  @override
  String get navExitTitle => 'Kilépés a navigációból?';

  @override
  String get navExitBody =>
      'Meg szeretné állítani a navigációt és visszatérni a térképre?';

  @override
  String get navContinue => 'Navigáció folytatása';

  @override
  String get navExit => 'Igen, kilépés';

  @override
  String get loadingInfo => 'Információk betöltése…';

  @override
  String get conditionsOnRoute => 'Feltételek az útvonalon';

  @override
  String get calculateRoute => 'Útvonal kiszámítása';

  @override
  String get sectionNavigationVoice => 'Navigációs hang';

  @override
  String get voiceGuidance => 'Hangos útmutatás';

  @override
  String get voiceGuidanceDesc =>
      'Kanyarodási utasítások felolvasása navigáció közben';

  @override
  String get testVoiceEngine => 'Hangmotor tesztelése';

  @override
  String get testVoiceEngineDesc =>
      'Koppintson a TTS motor ellenőrzéséhez és a beállítási útmutatóhoz';

  @override
  String get ttsDialogTitle => 'Hiányzó hangmotor';

  @override
  String get ttsDialogBody =>
      'Nem található működő Text-to-Speech motor.\n\nA Roadstr kizárólag nyílt forráskódú szoftverekre támaszkodik — telepítse az alábbi ingyenes motorok egyikét az F-Droidról:';

  @override
  String get ttsRhvoiceDesc =>
      'Természetesen hangzó hang, korlátozott nyelvlista';

  @override
  String get ttsEspeakDesc => 'Több mint 100 nyelvet fed le, robotikus hang';

  @override
  String get ttsInstallNote =>
      '⚠️ A telepítés után:\n1. Android beállítások → Kisegítő lehetőségek → Szövegfelolvasás\n2. Válassza ki az imént telepített motort\n3. Töltse le a nyelvéhez tartozó hangadatokat\n4. Indítsa újra teljesen a Roadstr-t';

  @override
  String get ttsTestNow => 'Tesztelés most';

  @override
  String get voiceUnsupportedTitle => 'Hangos útmutatás nem érhető el';

  @override
  String get voiceUnsupportedBody =>
      'Az Ön nyelve még nem támogatott a beszélt kanyarodási utasításokhoz. A navigációs utasítások továbbra is szövegként jelennek meg a képernyőn.';

  @override
  String get kokoroModelTitle => 'Hangmodell (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Nem letöltve · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Letöltés...';

  @override
  String get kokoroModelStatusReady => 'Hangmodell készen áll';

  @override
  String get kokoroModelDownloadBtn => 'Letöltés';

  @override
  String get kokoroModelSupportedLangs =>
      'Támogatja: olasz, angol, spanyol, francia, japán, kínai, portugál';

  @override
  String get autoDarkMode => 'Automatikus sötét téma';

  @override
  String get autoDarkModeDesc => 'Napnyugtakor aktiválja a sötét témát';

  @override
  String get settingsImperialUnits => 'Angolszász mértékegységek';

  @override
  String get settingsImperialUnitsDesc =>
      'Mérföld és láb kilométer és méter helyett';

  @override
  String get arrivedTitle => '🎉 Megérkezett!';

  @override
  String get arrivedBody => 'Elérte úticélját.';

  @override
  String get arrivedFeedbackPrompt => 'Hogy ment?';

  @override
  String get feedbackBad => 'Rossz';

  @override
  String get feedbackGood => 'Jól!';

  @override
  String get feedbackDialogTitle => 'Mondja el, mi ment rosszul';

  @override
  String get feedbackHint => 'Írja le a problémát…';

  @override
  String get feedbackSent => 'Visszajelzés elküldve — köszönjük! 🙏';

  @override
  String get feedbackSubmit => 'Küldés';

  @override
  String get transportModeCar => 'Autó';

  @override
  String get transportModeWalk => 'Gyalog';

  @override
  String etaArrivalLabel(String time) {
    return 'Érk. $time';
  }

  @override
  String get supportRoadstr => 'Támogassa a Roadstr-t';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address vágólapra másolva';
  }

  @override
  String get disclaimerTitle => 'Fontos értesítés';

  @override
  String get disclaimerAccept => 'Elolvastam és elfogadom';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Olvasd el a Wikipédián';

  @override
  String searchOnEngine(String engine) {
    return 'Keresés a(z) $engine on';
  }

  @override
  String get plannerFromHint => 'Honnan…';

  @override
  String get plannerToHint => 'Úticél…';

  @override
  String departEta(String dep, String arr) {
    return 'Indulás $dep  →  Megérkezés $arr';
  }

  @override
  String get modeCar => 'Autó';

  @override
  String get modeBike => 'Bicikli';

  @override
  String get modeWalk => 'Gyalog';

  @override
  String windSpeed(String speed) {
    return 'szél $speed km/h';
  }

  @override
  String durationMin(int m) {
    return '$m p';
  }

  @override
  String durationHourMin(int h, int m) {
    return '$hó ${m}p';
  }

  @override
  String get weatherClear => 'Derült';

  @override
  String get weatherPartlyCloudy => 'Változékony';

  @override
  String get weatherCloudy => 'Borult';

  @override
  String get weatherFog => 'Köd';

  @override
  String get weatherLightRain => 'Szitálás';

  @override
  String get weatherRain => 'Eső';

  @override
  String get weatherSnow => 'Hó';

  @override
  String get weatherShowers => 'Záporok';

  @override
  String get weatherThunderstorm => 'Zivatar';

  @override
  String get ztlAheadWarning =>
      'ZTL zóna előre — az útvonal korlátozott övezetbe vezet';

  @override
  String get ztlInsideWarning => 'Korlátozott forgalmú zóna';

  @override
  String get onboardingAppSubtitle => 'Nyílt forráskódú Nostr navigáció';

  @override
  String get onboardingWelcomeTitle => 'Üdvözöljük';

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
  String get onboardingGetStarted => 'Kezdés';

  @override
  String get onboardingNostrTitle => 'Nostr azonosság';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Csatlakozva';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (ajánlott)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec kulcs';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Érvénytelen nsec kulcs — ellenőrizze és próbálja újra.';

  @override
  String get onboardingSkip => 'Kihagyás most';

  @override
  String get onboardingContinue => 'Folytatás';

  @override
  String get onboardingEnterNsec => 'Adja meg az nsec kulcsot';

  @override
  String get onboardingSetupTitle => 'Roadstr beállítása';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Helyszín';

  @override
  String get onboardingLocationGranted => 'Helyszín-hozzáférés engedélyezve';

  @override
  String get onboardingLocationRequired => 'GPS navigációhoz szükséges';

  @override
  String get onboardingGrantButton => 'Engedélyezés';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS felhasználók';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'AI hangvezetés (opcionális)';

  @override
  String get onboardingVoiceReady => 'Kokoro hangmodell készen áll';

  @override
  String get onboardingVoiceDownloading => 'Hangmodell letöltése…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Letöltés';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Készen áll a navigálásra!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Induljunk!';

  @override
  String get onboardingProfileLoading => 'Profil betöltése…';

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
      'A maximális adatvédelem érdekében — a bejelentések a Nostr hálózaton terjednek — VPN használata ajánlott. Az ajánlott választás a Mullvad, amely Bitcoinnal fizethető.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'A megbízható működéshez állítsd az alkalmazás helymeghatározási engedélyét „Mindig engedélyezés”-re, ne csak használat közben.';
}
