// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Slovak (`sk`).
class AppLocalizationsSk extends AppLocalizations {
  AppLocalizationsSk([String locale = 'sk']) : super(locale);

  @override
  String get searchHint => 'Kam chcete ísť?';

  @override
  String get calculatingRoute => 'Počítam trasu…';

  @override
  String get routeNotFoundTitle => 'Trasa sa nenašla';

  @override
  String get noRouteFound => 'Trasa sa nenašla. Skontrolujte pripojenie.';

  @override
  String get emptyServerResponse =>
      'Prázdna odpoveď servera. Skontrolujte pripojenie.';

  @override
  String routeError(String error) {
    return 'Chyba výpočtu trasy: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS nie je dostupné — Nastavenia → Aplikácia → Roadstr → Oprávnenia → Poloha';

  @override
  String get acquiringGps => 'Získavam GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Server GraphHopper nie je nakonfigurovaný — používam OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'API kľúč GraphHopper nie je nastavený — používam OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'API kľúč OpenRouteService nie je nastavený — používam OSRM';

  @override
  String get chooseRoute => 'Vybrať trasu';

  @override
  String routeOptionsCount(int count) {
    return '$count možností';
  }

  @override
  String get cancel => 'Zrušiť';

  @override
  String get startNavigation => 'Spustiť navigáciu';

  @override
  String get fastestRoute => 'Najrýchlejšia';

  @override
  String get now => 'Teraz';

  @override
  String get history => 'História';

  @override
  String get clearHistory => 'Vymazať';

  @override
  String get selectedPosition => 'Vybraná poloha';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Ponuka';

  @override
  String get settingsTitle => 'Nastavenia';

  @override
  String get sectionTheme => 'Motív';

  @override
  String get sectionMap => 'Mapa';

  @override
  String get sectionPrivacy => 'Súkromie';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Jazyk';

  @override
  String get themeLightNostr => 'Svetlý · Nostr Fialová';

  @override
  String get themeLightBitcoin => 'Svetlý · Bitcoin Oranžová';

  @override
  String get langSystem => 'Predvolený systémový';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Nechať obrazovku zapnutú';

  @override
  String get keepScreenOnDescription =>
      'Zabraňuje prechodu do spánku počas navigácie';

  @override
  String get rotateMap => 'Mapa sleduje smer';

  @override
  String get rotateMapDescription => 'Mapa sa otáča podľa smeru jazdy';

  @override
  String get mapTileUrlLabel => 'URL dlaždíc mapy';

  @override
  String get routingProviderLabel => 'Poskytovateľ smerovania';

  @override
  String get osrmProvider => 'OSRM (verejný, bez kľúča)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokálny/súkromný)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API kľúč)';

  @override
  String get openrouteProvider => 'OpenRouteService (API kľúč)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'API kľúč GraphHopper (nepovinný)';

  @override
  String get verify => 'Overiť';

  @override
  String get graphhopperServerUrlRequired =>
      'Pred overením zadajte URL servera.';

  @override
  String get successTitle => 'Úspech';

  @override
  String get graphhopperServerReachable => 'Server GraphHopper je dostupný';

  @override
  String get errorTitle => 'Chyba';

  @override
  String get close => 'Zavrieť';

  @override
  String get privacyMode => 'Režim súkromia';

  @override
  String get privacyModeDescription => 'Neodosielať anonymné telemetrické dáta';

  @override
  String get infoVersion => 'Verzia';

  @override
  String get infoProtocol => 'Protokol';

  @override
  String get infoMaps => 'Mapy';

  @override
  String get infoRouting => 'Smerovanie';

  @override
  String get infoSource => 'Zdroj';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (vlastný hosting)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Nepripojený';

  @override
  String get loginWithNostrTitle => 'PRIHLÁSIŤ SA S NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Súkromný kľúč nikdy neopustí vaše zariadenie · Odporúčané';

  @override
  String get nsecLoginOption => 'Vložiť nsec';

  @override
  String get nsecLoginDescription =>
      'Súkromný kľúč uložený lokálne · Menej bezpečné';

  @override
  String get connectedViaAmber => 'Pripojené cez Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Pripojené cez nsec';

  @override
  String get publicKeyLabel => 'VEREJNÝ KĽÚČ';

  @override
  String get npubCopiedToClipboard => 'npub skopírovaný do schránky';

  @override
  String get logoutTitle => 'Odpojiť';

  @override
  String get logoutConfirmation =>
      'Odstrániť prihlasovacie údaje Nostr z tohto zariadenia?';

  @override
  String get logoutButton => 'Odpojiť';

  @override
  String get nostrIdentityInfo =>
      'S identitou Nostr môžete zverejňovať dopravné upozornenia, správy a zaujímavé miesta decentralizovane v sieti Nostr, bez centrálnych serverov.';

  @override
  String get warningTitle => 'Varovanie';

  @override
  String get nsecWarning =>
      'Chystáte sa zadať svoj súkromný kľúč Nostr priamo do aplikácie. Ktokoľvek s fyzickým alebo vzdialeným prístupom k vášmu zariadeniu ho môže prečítať a trvalo prevziať kontrolu nad vašou identitou Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  Bezpečnou metódou je Amber (NIP-55): nsec nikdy neopustí trezor podpisujúceho aplikácie.';

  @override
  String get nsecRiskAcknowledgment =>
      'Rozumiem riziku a napriek tomu chcem pokračovať';

  @override
  String get continueButton => 'Pokračovať';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber je podpisujúca aplikácia pre Android kompatibilná s NIP-55. Váš súkromný kľúč zostane izolovaný v Amberi a nikdy nebude zdieľaný.';

  @override
  String get requestKeyFromAmber => 'Požiadať Amber o verejný kľúč';

  @override
  String get amberNotFound =>
      'Amber nebol nájdený. Nainštalujte ho z Play Obchodu alebo zadajte svoj npub ručne.';

  @override
  String get waitingForAmberResponse => 'Čakám na odpoveď Amberu…';

  @override
  String get pasteNpubManually => 'Vložte svoj npub ručne:';

  @override
  String get confirmNpub => 'Potvrdiť npub';

  @override
  String get enterNsecTitle => 'Vložiť nsec';

  @override
  String get loginButton => 'Prihlásiť';

  @override
  String get invalidNpubTitle => 'Neplatný npub';

  @override
  String get invalidNsecTitle => 'Neplatný nsec';

  @override
  String get invalidNpubMessage => 'Uistite sa, že ste vložili správny npub.';

  @override
  String get invalidNsecMessage => 'Uistite sa, že ste vložili správny nsec.';

  @override
  String get amberResponseError => 'Chyba odpovede Amberu';

  @override
  String get ok => 'OK';

  @override
  String get or => 'alebo';

  @override
  String get gpsNotActiveTitle => 'GPS nie je aktívne';

  @override
  String get gpsDisabledMessage =>
      'Zapnite GPS v nastaveniach zariadenia, aby ste zistili polohu a mohli používať navigáciu.';

  @override
  String get openSettings => 'Nastavenia';

  @override
  String get myLocation => 'Moja poloha';

  @override
  String get loginToReport =>
      'Prihláste sa s Nostr (sekcia Profil) na hlásenie udalostí';

  @override
  String get navigateHere => 'Navigovať sem';

  @override
  String get reportEventHere => 'Nahlásiť udalosť tu';

  @override
  String get zapSendSats => 'Zap ⚡ (odoslať saty)';

  @override
  String get sendZap => 'Odoslať Zap';

  @override
  String get chooseAmountSats => 'Zvoľte sumu v satoshi (sats):';

  @override
  String get customAmount => 'Vlastná suma…';

  @override
  String get zapSending => 'Odosielam…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Načítavam Lightning adresu…';

  @override
  String get noLightningAddress => 'Tento reportér nemá Lightning adresu';

  @override
  String get requestingInvoice => 'Žiadam o faktúru…';

  @override
  String get lnurlUnavailable => 'LNURL nie je dostupné';

  @override
  String get invoiceFailed => 'Faktúru sa nepodarilo vygenerovať';

  @override
  String get openingWallet => 'Otváram peňaženku…';

  @override
  String get payingViaNwc => 'Platím cez NWC…';

  @override
  String get noLightningWallet => 'Nenašla sa žiadna Lightning peňaženka';

  @override
  String zapSent(int sats) {
    return '⚡ $sats satov odoslaných!';
  }

  @override
  String get stillThere => 'Stále tam';

  @override
  String get notThereAnymore => 'Už tam nie je';

  @override
  String get loginToConfirm =>
      'Prihláste sa s Nostr na potvrdenie alebo spochybnenie';

  @override
  String get reportAnEvent => 'Nahlásiť udalosť';

  @override
  String get optionalComment => 'Voliteľný komentár…';

  @override
  String get publish => 'Zverejniť';

  @override
  String get publishing => 'Zverejňujem…';

  @override
  String get reportPublished => 'Správa zverejnená ✓';

  @override
  String get myReports => 'MOJE SPRÁVY';

  @override
  String get noReportsYet => 'Žiadne správy neboli zverejnené';

  @override
  String get zapBalance => 'Zap zostatok';

  @override
  String get satoshiFromReports => 'Satoshi prijaté z vašich správ';

  @override
  String get reputationHigh => 'Vysoká';

  @override
  String get reputationMedium => 'Stredná';

  @override
  String get reputationLow => 'Nízka';

  @override
  String reputationLabel(String level) {
    return 'Reputácia $level';
  }

  @override
  String reliability(int pct) {
    return 'Spoľahlivosť: $pct%';
  }

  @override
  String get confirmedLabel => 'Potvrdené';

  @override
  String get removedLabel => 'Odstránené';

  @override
  String get positionLabel => 'Poloha';

  @override
  String get loadingLabel => 'Načítavam…';

  @override
  String get sectionWebSearch => 'Webové vyhľadávanie';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Vložte svoj NWC URI (Alby Hub, Mutiny, Cashu…) na platenie Zapov priamo z aplikácie.';

  @override
  String get searchEngineQwantDesc =>
      'Európsky, súkromie na prvom mieste. Žiadne sledovanie, žiadne reklamné profily. Odporúčané.';

  @override
  String get searchEngineBraveDesc =>
      'Nezávislý index, open-source. Žiadna závislosť od Google alebo Bing. Nulové profilovanie.';

  @override
  String get searchEngineDdgDesc =>
      'Zameraný na súkromie a populárny. Výsledky čiastočne z Bing — majte to na pamäti.';

  @override
  String get searchEngineStartpageDesc =>
      'Výsledky Google bez odovzdávania vašich dát Googlu. Rozumný kompromis.';

  @override
  String get searchEngineGoogleDesc =>
      'Veľmi efektívny. Ale pamätajte: Google vás pozná lepšie ako vaša mama a predáva všetko inzerentom. Vaša voľba. 🍪';

  @override
  String get categoryPolice => 'Polícia';

  @override
  String get categorySpeedCamera => 'Rýchlostný radar';

  @override
  String get categoryTrafficJam => 'Zápcha';

  @override
  String get categoryAccident => 'Nehoda';

  @override
  String get categoryRoadClosure => 'Uzávierka';

  @override
  String get categoryConstruction => 'Stavebné práce';

  @override
  String get categoryHazard => 'Nebezpečenstvo';

  @override
  String get categoryRoadCondition => 'Stav vozovky';

  @override
  String get categoryPothole => 'Výtlk';

  @override
  String get categoryFog => 'Hmla';

  @override
  String get categoryIce => 'Ľad';

  @override
  String get categoryAnimal => 'Zviera';

  @override
  String get categoryOther => 'Ostatné';

  @override
  String get dateTimeLabel => 'Dátum / čas';

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
}
