// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Czech (`cs`).
class AppLocalizationsCs extends AppLocalizations {
  AppLocalizationsCs([String locale = 'cs']) : super(locale);

  @override
  String get searchHint => 'Kam chcete jet?';

  @override
  String get calculatingRoute => 'Počítám trasu…';

  @override
  String get routeNotFoundTitle => 'Trasa nenalezena';

  @override
  String get noRouteFound => 'Trasa nenalezena. Zkontrolujte připojení.';

  @override
  String get emptyServerResponse =>
      'Prázdná odpověď serveru. Zkontrolujte připojení.';

  @override
  String routeError(String error) {
    return 'Chyba výpočtu trasy: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS nedostupné — Nastavení → Aplikace → Roadstr → Oprávnění → Poloha';

  @override
  String get acquiringGps => 'Získávám GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Server GraphHopper není nakonfigurován — používám OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'API klíč GraphHopper není nastaven — používám OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'API klíč OpenRouteService není nastaven — používám OSRM';

  @override
  String get chooseRoute => 'Vybrat trasu';

  @override
  String routeOptionsCount(int count) {
    return '$count možností';
  }

  @override
  String get cancel => 'Zrušit';

  @override
  String get startNavigation => 'Spustit navigaci';

  @override
  String get fastestRoute => 'Nejrychlejší';

  @override
  String get now => 'Teď';

  @override
  String get history => 'Historie';

  @override
  String get clearHistory => 'Smazat';

  @override
  String get selectedPosition => 'Vybraná poloha';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Nabídka';

  @override
  String get settingsTitle => 'Nastavení';

  @override
  String get sectionTheme => 'Motiv';

  @override
  String get sectionMap => 'Mapa';

  @override
  String get sectionPrivacy => 'Soukromí';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Jazyk';

  @override
  String get themeLightNostr => 'Světlý · Nostr Fialová';

  @override
  String get themeLightBitcoin => 'Světlý · Bitcoin Oranžová';

  @override
  String get langSystem => 'Výchozí systémový';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Ponechat obrazovku zapnutou';

  @override
  String get keepScreenOnDescription =>
      'Zabrání přechodu do spánku při navigaci';

  @override
  String get rotateMap => 'Mapa sleduje směr';

  @override
  String get rotateMapDescription => 'Mapa se otáčí podle směru jízdy';

  @override
  String get mapTileUrlLabel => 'URL dlaždic mapy';

  @override
  String get routingProviderLabel => 'Poskytovatel směrování';

  @override
  String get osrmProvider => 'OSRM (veřejný, bez klíče)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokální/soukromý)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API klíč)';

  @override
  String get openrouteProvider => 'OpenRouteService (API klíč)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'API klíč GraphHopper (nepovinný)';

  @override
  String get verify => 'Ověřit';

  @override
  String get graphhopperServerUrlRequired =>
      'Před ověřením zadejte URL serveru.';

  @override
  String get successTitle => 'Úspěch';

  @override
  String get graphhopperServerReachable => 'Server GraphHopper je dostupný';

  @override
  String get errorTitle => 'Chyba';

  @override
  String get close => 'Zavřít';

  @override
  String get privacyMode => 'Režim soukromí';

  @override
  String get privacyModeDescription => 'Neodesílat anonymní telemetrická data';

  @override
  String get infoVersion => 'Verze';

  @override
  String get infoProtocol => 'Protokol';

  @override
  String get infoMaps => 'Mapy';

  @override
  String get infoRouting => 'Směrování';

  @override
  String get infoSource => 'Zdroj';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (vlastní hosting)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Nepřipojen';

  @override
  String get loginWithNostrTitle => 'PŘIHLÁSIT S NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Soukromý klíč nikdy neopustí vaše zařízení · Doporučeno';

  @override
  String get nsecLoginOption => 'Vložit nsec';

  @override
  String get nsecLoginDescription =>
      'Soukromý klíč uložen lokálně · Méně bezpečné';

  @override
  String get connectedViaAmber => 'Připojeno přes Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Připojeno přes nsec';

  @override
  String get publicKeyLabel => 'VEŘEJNÝ KLÍČ';

  @override
  String get npubCopiedToClipboard => 'npub zkopírován do schránky';

  @override
  String get logoutTitle => 'Odpojit';

  @override
  String get logoutConfirmation =>
      'Odebrat přihlašovací údaje Nostr z tohoto zařízení?';

  @override
  String get logoutButton => 'Odpojit';

  @override
  String get nostrIdentityInfo =>
      'S identitou Nostr můžete zveřejňovat dopravní upozornění, zprávy a zajímavá místa decentralizovaně v síti Nostr, bez centrálních serverů.';

  @override
  String get warningTitle => 'Varování';

  @override
  String get nsecWarning =>
      'Chystáte se zadat svůj soukromý klíč Nostr přímo do aplikace. Kdokoli s fyzickým nebo vzdáleným přístupem k vašemu zařízení by ho mohl přečíst a trvale převzít kontrolu nad vaší identitou Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  Bezpečnou metodou je Amber (NIP-55): nsec nikdy neopustí trezor podpisujícího aplikace.';

  @override
  String get nsecRiskAcknowledgment =>
      'Rozumím riziku a přesto chci pokračovat';

  @override
  String get continueButton => 'Pokračovat';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber je podpisující aplikace pro Android kompatibilní s NIP-55. Váš soukromý klíč zůstane izolován uvnitř Amberu a nikdy nebude sdílen.';

  @override
  String get requestKeyFromAmber => 'Požádat Amber o veřejný klíč';

  @override
  String get amberNotFound =>
      'Amber nebyl nalezen. Nainstalujte ho z Play Store nebo zadejte svůj npub ručně.';

  @override
  String get waitingForAmberResponse => 'Čekám na odpověď Amberu…';

  @override
  String get pasteNpubManually => 'Vložte svůj npub ručně:';

  @override
  String get confirmNpub => 'Potvrdit npub';

  @override
  String get enterNsecTitle => 'Vložit nsec';

  @override
  String get loginButton => 'Přihlásit';

  @override
  String get invalidNpubTitle => 'Neplatný npub';

  @override
  String get invalidNsecTitle => 'Neplatný nsec';

  @override
  String get invalidNpubMessage => 'Ujistěte se, že jste vložili správný npub.';

  @override
  String get invalidNsecMessage => 'Ujistěte se, že jste vložili správný nsec.';

  @override
  String get amberResponseError => 'Chyba odpovědi Amberu';

  @override
  String get ok => 'OK';

  @override
  String get or => 'nebo';

  @override
  String get gpsNotActiveTitle => 'GPS není aktivní';

  @override
  String get gpsDisabledMessage =>
      'Zapněte GPS v nastavení zařízení, abyste zjistili polohu a používali navigaci.';

  @override
  String get openSettings => 'Nastavení';

  @override
  String get myLocation => 'Moje poloha';

  @override
  String get loginToReport =>
      'Přihlaste se s Nostr (sekce Profil) pro hlášení událostí';

  @override
  String get navigateHere => 'Navigovat sem';

  @override
  String get reportEventHere => 'Nahlásit událost zde';

  @override
  String get zapSendSats => 'Zap ⚡ (odeslat saty)';

  @override
  String get sendZap => 'Odeslat Zap';

  @override
  String get chooseAmountSats => 'Zvolte částku v satoshi (sats):';

  @override
  String get customAmount => 'Vlastní částka…';

  @override
  String get zapSending => 'Odesílám…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Načítám Lightning adresu…';

  @override
  String get noLightningAddress => 'Tento reportér nemá Lightning adresu';

  @override
  String get requestingInvoice => 'Žádám o fakturu…';

  @override
  String get lnurlUnavailable => 'LNURL není dostupné';

  @override
  String get invoiceFailed => 'Fakturu se nepodařilo vygenerovat';

  @override
  String get openingWallet => 'Otevírám peněženku…';

  @override
  String get payingViaNwc => 'Platím přes NWC…';

  @override
  String get noLightningWallet => 'Nenalezena žádná Lightning peněženka';

  @override
  String zapSent(int sats) {
    return '⚡ $sats satů odesláno!';
  }

  @override
  String get stillThere => 'Stále tam';

  @override
  String get notThereAnymore => 'Už tam není';

  @override
  String get loginToConfirm =>
      'Přihlaste se s Nostr pro potvrzení nebo zpochybnění';

  @override
  String get reportAnEvent => 'Nahlásit událost';

  @override
  String get optionalComment => 'Volitelný komentář…';

  @override
  String get publish => 'Zveřejnit';

  @override
  String get publishing => 'Zveřejňuji…';

  @override
  String get reportPublished => 'Zpráva zveřejněna ✓';

  @override
  String get myReports => 'MOJE ZPRÁVY';

  @override
  String get noReportsYet => 'Žádné zprávy nebyly zveřejněny';

  @override
  String get zapBalance => 'Zap zůstatek';

  @override
  String get satoshiFromReports => 'Satoshi přijaté z vašich zpráv';

  @override
  String get reputationHigh => 'Vysoká';

  @override
  String get reputationMedium => 'Střední';

  @override
  String get reputationLow => 'Nízká';

  @override
  String reputationLabel(String level) {
    return 'Reputace $level';
  }

  @override
  String reliability(int pct) {
    return 'Spolehlivost: $pct%';
  }

  @override
  String get confirmedLabel => 'Potvrzeno';

  @override
  String get removedLabel => 'Odstraněno';

  @override
  String get positionLabel => 'Poloha';

  @override
  String get loadingLabel => 'Načítám…';

  @override
  String get sectionWebSearch => 'Webové vyhledávání';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Vložte svůj NWC URI (Alby Hub, Mutiny, Cashu…) pro platbu Zapů přímo z aplikace.';

  @override
  String get searchEngineQwantDesc =>
      'Evropský, soukromí na prvním místě. Žádné sledování, žádné reklamní profily. Doporučeno.';

  @override
  String get searchEngineBraveDesc =>
      'Nezávislý index, open-source. Žádná závislost na Google nebo Bing. Nulové profilování.';

  @override
  String get searchEngineDdgDesc =>
      'Zaměřený na soukromí a populární. Výsledky částečně z Bing — mějte to na paměti.';

  @override
  String get searchEngineStartpageDesc =>
      'Výsledky Google bez předávání vašich dat Googlu. Rozumný kompromis.';

  @override
  String get searchEngineGoogleDesc =>
      'Velmi efektivní. Ale pamatujte: Google vás zná lépe než vaše máma a prodává vše inzerentům. Váš výběr. 🍪';

  @override
  String get categoryPolice => 'Policie';

  @override
  String get categorySpeedCamera => 'Rychlostní radar';

  @override
  String get categoryTrafficJam => 'Zácpa';

  @override
  String get categoryAccident => 'Nehoda';

  @override
  String get categoryRoadClosure => 'Uzavírka';

  @override
  String get categoryConstruction => 'Stavební práce';

  @override
  String get categoryHazard => 'Nebezpečí';

  @override
  String get categoryRoadCondition => 'Stav vozovky';

  @override
  String get categoryPothole => 'Výtluk';

  @override
  String get categoryFog => 'Mlha';

  @override
  String get categoryIce => 'Led';

  @override
  String get categoryAnimal => 'Zvíře';

  @override
  String get categoryOther => 'Ostatní';

  @override
  String get dateTimeLabel => 'Datum / čas';

  @override
  String minutesAgo(int count) {
    return 'před $count min';
  }

  @override
  String hoursAgo(int count) {
    return 'před ${count}h';
  }

  @override
  String daysAgo(int count) {
    return 'před ${count}d';
  }

  @override
  String get sectionFavorites => 'Uložená místa';

  @override
  String get addFavorite => 'Přidat místo';

  @override
  String get favoriteLabelHint => 'Název (např. Domov, Kancelář)';

  @override
  String get favoriteAddressHint => 'Adresa';

  @override
  String get favoriteGeocodingError =>
      'Adresa nenalezena. Zkus přesnější adresu.';
}
