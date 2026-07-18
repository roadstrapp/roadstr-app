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
  String get autoCenterOnLaunch => 'Indításkor középre igazítás a helyzetemre';

  @override
  String get autoCenterOnLaunchDesc =>
      'Csak akkor használja automatikusan a GPS-t, ha a helyengedélyt már megadták';

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
      'A Roadstr egy kísérleti, nyílt forráskódú, közösség által karbantartott navigációs alkalmazás, amely az OpenStreetMap adatain és a Nostr protokollon alapul, és bármely országban használható. Az alkalmazás letöltésével, telepítésével vagy használatával a felhasználó feltétel nélkül elfogadja az alábbi valamennyi feltételt, területi korlátozás nélkül.\n\n🚗 KÖZÚTI BIZTONSÁG MINDENEKELŐTT\nA vezetőnek mindig az útra kell figyelnie, és be kell tartania minden alkalmazandó közlekedési szabályt és kihelyezett jelzést, amelyek mindig elsőbbséget élveznek az alkalmazás bármely utasításával szemben. Soha ne kezelje a készüléket vezetés közben; elindulás előtt rögzítse azt egy jóváhagyott, jól látható tartóban, és amíg a jármű mozgásban van, soha ne terelje el a figyelmét az útról a készülékkel való interakció céljából.\n\n⚠️ KOCKÁZATVÁLLALÁS — VILÁGSZERTE\nA Roadstr használatával, bármely országban és bármely jogrendszer alapján, a felhasználó tudatosan és önkéntesen vállalja az annak használatával összefüggő ÖSSZES kockázatot, ideértve, de nem kizárólagosan: közlekedési baleseteket, személyi sérülést, halált, vagyoni kárt, járműkárt, bírságokat, közigazgatási szankciókat, elszállítást, lefoglalást, büntetőjogi felelősséget, vagy bármely más, az alkalmazásra való hagyatkozásból közvetlenül vagy közvetve eredő következményt. Kizárólag a felhasználó viseli a teljes felelősséget minden vezetési és navigációs döntésért.\n\n🚫 SZAVATOSSÁG KIZÁRÁSA\nA Roadstr szigorúan „ADOTT ÁLLAPOTBAN” („AS IS”) és „AMENNYIBEN ELÉRHETŐ” („AS AVAILABLE”) alapon kerül rendelkezésre bocsátásra, mindennemű szavatosság nélkül, legyen az kifejezett, hallgatólagos vagy jogszabályon alapuló — ideértve, korlátozás nélkül, a pontosságra, teljességre, megbízhatóságra, elérhetőségre, forgalomképességre, adott célra való alkalmasságra és jogtisztaságra vonatkozó szavatosságot. A térképadatok, útvonaltervezés, sebességhatárok, sebességmérő kamerák és korlátozott forgalmú övezetekre (ZTL/ZAC/LTZ) vonatkozó információk nyílt, közösség által karbantartott forrásokból (OpenStreetMap, Overpass API) származnak, amelyek bármely ország, régió vagy önkormányzat tekintetében, bármikor és előzetes értesítés nélkül hiányosak, elavultak vagy pontatlanok lehetnek. Kizárólag a felhasználó felelőssége, hogy az utazás előtt és alatt önállóan ellenőrizze bármely javasolt útvonal jogszerűségét és járhatóságát a hivatalos helyi jelzésekkel és szabályozásokkal összevetve.\n\n📍 PONTOSSÁG ÉS GPS\nA GPS-helymeghatározás pontatlan vagy elérhetetlen lehet. Minden útirányítás, távolságadat és figyelmeztetés kizárólag tájékoztató jellegű, és soha nem tekinthető egy vezetési döntés kizárólagos alapjának.\n\n🛡️ FELELŐSSÉG KORLÁTOZÁSA\nAz alkalmazandó jog által megengedett legteljesebb mértékben, bármely joghatóságban, a fejlesztők, a közreműködők és a Roadstr létrehozásában vagy terjesztésében részt vevő bármely fél nem tehető felelőssé semmilyen közvetlen, közvetett, véletlenszerű, következményes, különleges, példaértékű vagy büntető jellegű kárért — ideértve a személyi sérülést, halált vagy anyagi veszteséget is —, amely az alkalmazás használatából vagy használatának ellehetetlenüléséből ered vagy azzal összefüggésbe hozható, még akkor is, ha tájékoztatták őket az ilyen károk bekövetkezésének lehetőségéről. Amennyiben egy joghatóság e korlátozás egy részét vagy egészét nem teszi lehetővé, a felelősség az adott joghatóságban jogszabály által megengedett legkisebb mértékre korlátozódik.\n\n🤝 KÁRTALANÍTÁS\nA felhasználó vállalja, hogy kártalanítja és mentesíti a fejlesztőket és a közreműködőket minden olyan igény, kár, veszteség vagy költség (ideértve az ügyvédi díjakat is) alól, amely a felhasználó általi alkalmazáshasználatból vagy a jelen feltételek megsértéséből ered.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ ELVÁLASZTHATÓSÁG\nAmennyiben a jelen feltételek bármely rendelkezése egy adott joghatóságban végrehajthatatlannak minősül, az adott rendelkezés a szükséges legkisebb mértékben korlátozandó vagy elhagyandó, és a fennmaradó rendelkezések továbbra is teljes hatállyal érvényben maradnak.\n\nA Roadstr világ bármely pontján történő használatával a felhasználó megerősíti, hogy elolvasta, megértette és feltétel nélkül elfogadta a fenti valamennyi feltételt, és teljes és korlátlan felelősséget — valamint minden kockázatot — vállal az alkalmazás használatáért és az abból eredő bármely következményért.';

  @override
  String get readOnWikipedia => 'Olvasd el a Wikipédián';

  @override
  String get openInBrowser => 'Megnyitás böngészőben';

  @override
  String get wikipediaLoadFailed =>
      'A Wikipédia nem tölthető be biztonságosan.';

  @override
  String get retry => 'Újra';

  @override
  String get poiDetailsFromOsm => 'Információk az OpenStreetMapről';

  @override
  String get poiCategory => 'Kategória';

  @override
  String get poiOperator => 'Üzemeltető';

  @override
  String get poiCuisine => 'Konyha';

  @override
  String get poiAccessibility => 'Akadálymentesség';

  @override
  String get poiWheelchairYes => 'Kerekesszékkel megközelíthető';

  @override
  String get poiWheelchairLimited =>
      'Korlátozottan megközelíthető kerekesszékkel';

  @override
  String get poiWheelchairNo => 'Kerekesszékkel nem megközelíthető';

  @override
  String get poiContact => 'Kapcsolat';

  @override
  String get poiAddress => 'Cím';

  @override
  String get poiWebsite => 'Webhely';

  @override
  String get gpsSignalLost => 'A GPS-jel megszakadt';

  @override
  String get poiOpenNow => 'Most nyitva';

  @override
  String get poiClosedNow => 'Zárva';

  @override
  String poiOpensAt(String when) {
    return 'Nyit: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Zár: $when';
  }

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
      'Korlátozott forgalmú zóna következik — az útvonal áthalad rajta';

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
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

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
  String get kokoroVoiceGenderTitle => 'Hang';

  @override
  String get kokoroVoiceFemale => 'Női';

  @override
  String get kokoroVoiceMale => 'Férfi';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Férfi hang nem érhető el ehhez a nyelvhez';

  @override
  String get kokoroSpeedTitle => 'Beszédsebesség';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Nem kötelező: a mentett kedvenceid szinkronizálódhatnak az eszközeid között a Nostr relék segítségével, végponttól végpontig titkosítva (NIP-44) a saját kulcsoddal — a relék csak titkosított szöveget látnak, és rajtad kívül senki sem olvashatja a tartalmat. Bármikor bekapcsolhatod a Beállításokban.';

  @override
  String get parkingSaveHere => 'Parkolás mentése ide';

  @override
  String get parkingMarkerTitle => 'Parkolóhely';

  @override
  String get parkingNavigateHere => 'Navigálás a parkoláshoz';

  @override
  String get parkingRemove => 'Parkolás eltávolítása';

  @override
  String get parkingSavedSnack => 'Parkolóhely mentve';

  @override
  String get parkingRemovedSnack => 'Parkolóhely eltávolítva';

  @override
  String get exportFavoritesTitle => 'Kedvencek exportálása';

  @override
  String get exportFavoritesDesc =>
      'Mentsd el kedvenc helyeidet egy JSON-fájlba, amelyet biztonsági mentésként megőrizhetsz vagy másik eszközre vihetsz.';

  @override
  String get exportEncryptToggle => 'Titkosítás jelszóval';

  @override
  String get exportPasswordHint => 'Jelszó';

  @override
  String get exportButton => 'Exportálás';

  @override
  String get exportSuccessSnack => 'Kedvencek exportálva';

  @override
  String get exportFailedSnack => 'Az exportálás sikertelen';

  @override
  String get importFavoritesTitle => 'Kedvencek importálása';

  @override
  String get importPasswordPrompt =>
      'Ez a fájl titkosított — add meg a jelszót';

  @override
  String importSuccessSnack(int n) {
    return '$n kedvenc importálva';
  }

  @override
  String get importFailedSnack =>
      'Az importálás sikertelen — ellenőrizd a fájlt és a jelszót';

  @override
  String get syncFavoritesTitle => 'Kedvencek szinkronizálása';

  @override
  String get syncFavoritesDesc =>
      'Tedd közzé kedvenceidet végponttól végpontig titkosítva a Nostr reléiden, hogy minden eszközödön kövessenek. A relék csak titkosított szöveget látnak — rajtad kívül senki sem olvashatja a tartalmat.';

  @override
  String get syncNowButton => 'Küldés a Nostrra';

  @override
  String get syncPullButton => 'Letöltés a Nostrról';

  @override
  String get syncPassphraseTitle => 'Szinkronizálási jelmondat (opcionális)';

  @override
  String get syncPassphraseDesc =>
      'Második titkosítási réteg a szinkronizált kedvencekhez: még ha a Nostr-kulcsa kompromittálódna is, a relék pillanatképe e jelmondat nélkül olvashatatlan marad. Minden új eszközön egyszer kell megadnia. Hagyja üresen a kikapcsoláshoz.';

  @override
  String get syncSuccessSnack => 'Kedvencek szinkronizálva';

  @override
  String get syncFailedSnack => 'A szinkronizálás sikertelen';

  @override
  String syncLastSyncLabel(String when) {
    return 'Utolsó szinkronizálás: $when';
  }

  @override
  String get syncNoIdentity =>
      'Jelentkezz be Nostrral (Beállítások → Profil) a szinkronizálás engedélyezéséhez';

  @override
  String get onboardingVpnNotice =>
      'A maximális adatvédelem érdekében — a bejelentések a Nostr hálózaton terjednek — VPN használata ajánlott. Az ajánlott választás a Mullvad, amely Bitcoinnal fizethető.';

  @override
  String get trafficNormal => 'Normál forgalom';

  @override
  String get trafficModerate => 'Mérsékelt forgalom';

  @override
  String get trafficHeavy => 'Erős forgalom';

  @override
  String get avoidHighwaysChip => 'Autópályák elkerülése';

  @override
  String get avoidTollsChip => 'Fizetős utak elkerülése';

  @override
  String get preferShorterChip => 'Legrövidebb útvonal';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Útvonal előnézete';
}
