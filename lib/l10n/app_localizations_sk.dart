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
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Jazyk';

  @override
  String get themeLightNostr => 'Svetlý · Nostr Fialová';

  @override
  String get themeLightBitcoin => 'Svetlý · Bitcoin Oranžová';

  @override
  String get themeDarkNostr => 'Tmavý · Nostr Fialová';

  @override
  String get themeDarkBitcoin => 'Tmavý · Bitcoin Oranžová';

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

  @override
  String get sectionFavorites => 'Uložené miesta';

  @override
  String get addFavorite => 'Pridať miesto';

  @override
  String get favoriteLabelHint => 'Názov (napr. Domov, Kancelária)';

  @override
  String get favoriteAddressHint => 'Adresa';

  @override
  String get favoriteGeocodingError =>
      'Adresa sa nenašla. Skús presnejšiu adresu.';

  @override
  String get trafficAlertTitle => 'Nová premávka na trase';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category nahlásené $age na vašej trase.\n\nChcete nájsť alternatívnu trasu?';
  }

  @override
  String get trafficContinue => 'Pokračovať';

  @override
  String get trafficRecalculate => 'Prepočítať trasu';

  @override
  String get navExitTitle => 'Ukončiť navigáciu?';

  @override
  String get navExitBody => 'Chcete zastaviť navigáciu a vrátiť sa na mapu?';

  @override
  String get navContinue => 'Pokračovať v navigácii';

  @override
  String get navExit => 'Áno, ukončiť';

  @override
  String get loadingInfo => 'Načítavanie informácií…';

  @override
  String get conditionsOnRoute => 'Podmienky na trase';

  @override
  String get calculateRoute => 'Vypočítať trasu';

  @override
  String get sectionNavigationVoice => 'Hlas navigácie';

  @override
  String get voiceGuidance => 'Hlasové navádzanie';

  @override
  String get voiceGuidanceDesc =>
      'Čítať pokyny na odbočenie nahlas počas navigácie';

  @override
  String get testVoiceEngine => 'Otestovať hlasový engine';

  @override
  String get testVoiceEngineDesc =>
      'Klepnutím skontrolujte TTS engine a získajte pokyny na nastavenie';

  @override
  String get ttsDialogTitle => 'Chýba hlasový engine';

  @override
  String get ttsDialogBody =>
      'Nebol nájdený žiadny funkčný engine Text-to-Speech.\n\nRoadstr sa spolieha výlučne na open source softvér — nainštalujte si jeden z týchto bezplatných enginov z F-Droid:';

  @override
  String get ttsRhvoiceDesc =>
      'Prirodzene znejúci hlas, obmedzený zoznam jazykov';

  @override
  String get ttsEspeakDesc => 'Pokrýva viac ako 100 jazykov, robotický hlas';

  @override
  String get ttsInstallNote =>
      '⚠️ Po inštalácii:\n1. Nastavenia Androidu → Dostupnosť → Prevod textu na reč\n2. Vyberte práve nainštalovaný engine\n3. Stiahnite hlasové dáta pre váš jazyk\n4. Reštartujte Roadstr úplne';

  @override
  String get ttsTestNow => 'Otestovať teraz';

  @override
  String get voiceUnsupportedTitle => 'Hlasové navádzanie nie je k dispozícii';

  @override
  String get voiceUnsupportedBody =>
      'Váš jazyk zatiaľ nie je podporovaný pre hlasové pokyny na odbočenie. Pokyny na navigáciu sa budú naďalej zobrazovať ako text na obrazovke.';

  @override
  String get kokoroModelTitle => 'Hlasový model (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Nestiahnuté · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Stiahnutie...';

  @override
  String get kokoroModelStatusReady => 'Hlasový model je pripravený';

  @override
  String get kokoroModelDownloadBtn => 'Stiahnuť';

  @override
  String get kokoroModelSupportedLangs =>
      'Podporuje: taliančinu, angličtinu, španielčinu, francúzštinu, japončinu, čínštinu, portugalčinu';

  @override
  String get autoDarkMode => 'Automatická tmavá téma';

  @override
  String get autoDarkModeDesc => 'Aktivuje tmavú tému pri západe slnka';

  @override
  String get arrivedTitle => '🎉 Dorazili ste!';

  @override
  String get arrivedBody => 'Dosiahli ste svoje cieľové miesto.';

  @override
  String get arrivedFeedbackPrompt => 'Ako to išlo?';

  @override
  String get feedbackBad => 'Zle';

  @override
  String get feedbackGood => 'Dobre!';

  @override
  String get feedbackDialogTitle => 'Povedzte nám, čo sa pokazilo';

  @override
  String get feedbackHint => 'Opíšte problém…';

  @override
  String get feedbackSent => 'Spätná väzba odoslaná — ďakujeme! 🙏';

  @override
  String get feedbackSubmit => 'Odoslať';

  @override
  String get transportModeCar => 'Auto';

  @override
  String get transportModeWalk => 'Pešo';

  @override
  String etaArrivalLabel(String time) {
    return 'Príj. $time';
  }

  @override
  String get supportRoadstr => 'Podporte Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address skopírované do schránky';
  }

  @override
  String get disclaimerTitle => 'Dôležité upozornenie';

  @override
  String get disclaimerAccept => 'Prečítal/a som a súhlasím';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Čítať na Wikipédii';

  @override
  String searchOnEngine(String engine) {
    return 'Hľadať na $engine';
  }

  @override
  String get plannerFromHint => 'Odkiaľ…';

  @override
  String get plannerToHint => 'Cieľ…';

  @override
  String departEta(String dep, String arr) {
    return 'Odchod $dep  →  Príchod $arr';
  }
}
