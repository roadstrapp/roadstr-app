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
  String get sectionPrivacy => 'Adatvédelem';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Nyelv';

  @override
  String get themeLightNostr => 'Világos · Nostr Lila';

  @override
  String get themeLightBitcoin => 'Világos · Bitcoin Narancs';

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
  String get privacyMode => 'Adatvédelmi mód';

  @override
  String get privacyModeDescription =>
      'Ne küldjön névtelen telemetriai adatokat';

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
}
