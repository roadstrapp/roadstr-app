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
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Jazyk';

  @override
  String get themeLightNostr => 'Světlý · Nostr Fialová';

  @override
  String get themeLightBitcoin => 'Světlý · Bitcoin Oranžová';

  @override
  String get themeDarkNostr => 'Tmavý · Nostr Fialová';

  @override
  String get themeDarkBitcoin => 'Tmavý · Bitcoin Oranžová';

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
  String get autoCenterOnLaunch => 'Při spuštění vycentrovat na mou polohu';

  @override
  String get autoCenterOnLaunchDesc =>
      'Automaticky použije GPS, pouze pokud již bylo uděleno oprávnění k poloze';

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

  @override
  String get trafficAlertTitle => 'Nový provoz na trase';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category hlášeno $age na vaší trase.\n\nChcete najít alternativní trasu?';
  }

  @override
  String get trafficContinue => 'Pokračovat';

  @override
  String get trafficRecalculate => 'Přepočítat trasu';

  @override
  String get navExitTitle => 'Ukončit navigaci?';

  @override
  String get navExitBody => 'Chcete zastavit navigaci a vrátit se na mapu?';

  @override
  String get navContinue => 'Pokračovat v navigaci';

  @override
  String get navExit => 'Ano, ukončit';

  @override
  String get loadingInfo => 'Načítání informací…';

  @override
  String get conditionsOnRoute => 'Podmínky na trase';

  @override
  String get calculateRoute => 'Vypočítat trasu';

  @override
  String get sectionNavigationVoice => 'Hlas navigace';

  @override
  String get voiceGuidance => 'Hlasové navádění';

  @override
  String get voiceGuidanceDesc =>
      'Číst pokyny k odbočení nahlas během navigace';

  @override
  String get testVoiceEngine => 'Otestovat hlasový engine';

  @override
  String get testVoiceEngineDesc =>
      'Klepnutím zkontrolujte TTS engine a získejte pokyny k nastavení';

  @override
  String get ttsDialogTitle => 'Chybí hlasový engine';

  @override
  String get ttsDialogBody =>
      'Nebyl nalezen žádný funkční engine Text-to-Speech.\n\nRoadstr se spoléhá pouze na open source software — nainstalujte si jeden z těchto bezplatných enginů z F-Droid:';

  @override
  String get ttsRhvoiceDesc => 'Přirozeně znějící hlas, omezený seznam jazyků';

  @override
  String get ttsEspeakDesc => 'Pokrývá více než 100 jazyků, robotický hlas';

  @override
  String get ttsInstallNote =>
      '⚠️ Po instalaci:\n1. Nastavení Androidu → Přístupnost → Převod textu na řeč\n2. Vyberte právě nainstalovaný engine\n3. Stáhněte hlasová data pro váš jazyk\n4. Roadstr úplně restartujte';

  @override
  String get ttsTestNow => 'Otestovat nyní';

  @override
  String get voiceUnsupportedTitle => 'Hlasové navádění není k dispozici';

  @override
  String get voiceUnsupportedBody =>
      'Váš jazyk zatím není podporován pro hlasové pokyny k odbočení. Pokyny k navigaci se budou nadále zobrazovat jako text na obrazovce.';

  @override
  String get kokoroModelTitle => 'Hlasový model (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Nestaženo · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Stahování...';

  @override
  String get kokoroModelStatusReady => 'Hlasový model je připraven';

  @override
  String get kokoroModelDownloadBtn => 'Stáhnout';

  @override
  String get kokoroModelSupportedLangs =>
      'Podporuje: italštinu, angličtinu, španělštinu, francouzštinu, japonštinu, čínštinu, portugalštinu';

  @override
  String get autoDarkMode => 'Automatický tmavý režim';

  @override
  String get autoDarkModeDesc => 'Aktivuje tmavý motiv při západu slunce';

  @override
  String get settingsImperialUnits => 'Imperiální jednotky';

  @override
  String get settingsImperialUnitsDesc =>
      'Míle a stopy místo kilometrů a metrů';

  @override
  String get arrivedTitle => '🎉 Dorazili jste!';

  @override
  String get arrivedBody => 'Dosáhli jste svého cíle.';

  @override
  String get arrivedFeedbackPrompt => 'Jak to šlo?';

  @override
  String get feedbackBad => 'Špatně';

  @override
  String get feedbackGood => 'Dobře!';

  @override
  String get feedbackDialogTitle => 'Řekněte nám, co se pokazilo';

  @override
  String get feedbackHint => 'Popište problém…';

  @override
  String get feedbackSent => 'Zpětná vazba odeslána — děkujeme! 🙏';

  @override
  String get feedbackSubmit => 'Odeslat';

  @override
  String get transportModeCar => 'Auto';

  @override
  String get transportModeWalk => 'Pěšky';

  @override
  String etaArrivalLabel(String time) {
    return 'Příj. $time';
  }

  @override
  String get supportRoadstr => 'Podpořte Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address zkopírováno do schránky';
  }

  @override
  String get disclaimerTitle => 'Důležité upozornění';

  @override
  String get disclaimerAccept => 'Přečetl/a jsem a souhlasím';

  @override
  String get disclaimerBody =>
      'Roadstr je experimentální, open-source navigační aplikace udržovaná komunitou, založená na datech OpenStreetMap a protokolu Nostr, zpřístupněná k použití v jakékoli zemi. Stažením, instalací nebo používáním této aplikace uživatel bezpodmínečně přijímá všechny následující podmínky, bez územního omezení.\n\n🚗 BEZPEČNOST SILNIČNÍHO PROVOZU NA PRVNÍM MÍSTĚ\nŘidič musí mít vždy oči na silnici a dodržovat všechny platné dopravní předpisy a umístěné dopravní značení, které má vždy přednost před jakýmkoli pokynem aplikace. Nikdy neobsluhujte zařízení během jízdy; před vyjetím jej upevněte do schváleného, viditelného držáku a nikdy neodvádějte pozornost od silnice, abyste s ním interagovali, zatímco se vozidlo pohybuje.\n\n⚠️ PŘEVZETÍ RIZIKA — CELOSVĚTOVĚ\nPoužíváním aplikace Roadstr, v jakékoli zemi a podle jakéhokoli právního řádu, uživatel vědomě a dobrovolně přebírá VEŠKERÁ rizika spojená s jejím používáním, mimo jiné včetně: dopravních nehod, újmy na zdraví, úmrtí, škody na majetku, poškození vozidla, pokut, správních sankcí, odtahu, zabavení vozidla, trestní odpovědnosti nebo jakéhokoli jiného následku vyplývajícího přímo či nepřímo ze spoléhání se na aplikaci. Výhradně uživatel nese plnou odpovědnost za každé rozhodnutí týkající se řízení a navigace.\n\n🚫 ŽÁDNÁ ZÁRUKA\nAplikace Roadstr je poskytována výhradně „TAK, JAK JE“ a „JAK JE DOSTUPNÁ“, bez jakékoli záruky jakéhokoli druhu, ať výslovné, předpokládané nebo zákonné — včetně, bez omezení, záruk přesnosti, úplnosti, spolehlivosti, dostupnosti, obchodovatelnosti, vhodnosti pro konkrétní účel a neporušování práv. Mapová data, trasování, rychlostní limity, rychlostní kamery a informace o zónách s omezeným provozem (ZTL/ZAC/LTZ) pocházejí z otevřených, komunitou udržovaných zdrojů (OpenStreetMap, Overpass API), které mohou být pro kteroukoli zemi, region či obec kdykoli a bez upozornění neúplné, zastaralé nebo nepřesné. Uživatel je sám výhradně odpovědný za to, že si před cestou i během ní nezávisle ověří zákonnost a průjezdnost jakékoli navržené trasy podle oficiálního místního značení a předpisů.\n\n📍 PŘESNOST A GPS\nUrčení polohy pomocí GPS může být nepřesné nebo nedostupné. Veškeré pokyny, vzdálenosti a upozornění slouží pouze jako orientační pomůcka a nikdy se na ně nelze spoléhat jako na jediný podklad pro rozhodnutí při řízení.\n\n🛡️ OMEZENÍ ODPOVĚDNOSTI\nV nejširším rozsahu povoleném platným právem v kterékoli jurisdikci vývojáři, přispěvatelé a jakákoli strana podílející se na vytvoření nebo šíření aplikace Roadstr neodpovídají za žádné přímé, nepřímé, náhodné, následné, zvláštní, exemplární ani represivní škody jakéhokoli druhu — včetně újmy na zdraví, úmrtí či finanční ztráty — vzniklé z používání či nemožnosti používání aplikace nebo s ním související, a to i v případě, že byli na možnost vzniku takových škod upozorněni. Pokud daná jurisdikce některé nebo veškeré toto omezení nepřipouští, je odpovědnost omezena v nejmenším rozsahu přípustném podle práva dané jurisdikce.\n\n🤝 ODŠKODNĚNÍ\nUživatel se zavazuje odškodnit vývojáře a přispěvatele a zbavit je odpovědnosti za jakýkoli nárok, škodu, ztrátu nebo náklad (včetně nákladů na právní zastoupení) vyplývající z používání aplikace uživatelem nebo z porušení těchto podmínek.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ ODDĚLITELNOST USTANOVENÍ\nBude-li některé ustanovení těchto podmínek v dané jurisdikci shledáno nevymahatelným, bude toto ustanovení omezeno nebo vyňato v minimálním nezbytném rozsahu a všechna ostatní ustanovení zůstanou v plné platnosti a účinnosti.\n\nPoužíváním aplikace Roadstr, kdekoli na světě, uživatel potvrzuje, že si přečetl, porozuměl a bezpodmínečně přijal veškeré výše uvedené podmínky, a přebírá plnou a úplnou odpovědnost — a veškeré riziko — za používání aplikace a jakýkoli z toho vyplývající následek.';

  @override
  String get readOnWikipedia => 'Číst na Wikipedii';

  @override
  String get openInBrowser => 'Otevřít v prohlížeči';

  @override
  String get wikipediaLoadFailed => 'Wikipedii se nepodařilo bezpečně načíst.';

  @override
  String get retry => 'Zkusit znovu';

  @override
  String get poiDetailsFromOsm => 'Informace z OpenStreetMap';

  @override
  String get poiCategory => 'Kategorie';

  @override
  String get poiOperator => 'Provozovatel';

  @override
  String get poiCuisine => 'Kuchyně';

  @override
  String get poiAccessibility => 'Přístupnost';

  @override
  String get poiWheelchairYes => 'Bezbariérový přístup';

  @override
  String get poiWheelchairLimited => 'Omezený bezbariérový přístup';

  @override
  String get poiWheelchairNo => 'Bez bezbariérového přístupu';

  @override
  String get poiContact => 'Kontakt';

  @override
  String get poiAddress => 'Adresa';

  @override
  String get poiWebsite => 'Webová stránka';

  @override
  String get poiAccessPrivate => 'Private access';

  @override
  String get poiAccessCustomers => 'Customers only';

  @override
  String get poiAccessPermit => 'Permit required';

  @override
  String get poiAccessNo => 'No public access';

  @override
  String get poiAccessDestination => 'Destination access only';

  @override
  String get poiLightningAccepted => 'Lightning accepted';

  @override
  String get poiBitcoinAccepted => 'Bitcoin accepted';

  @override
  String get poiParkingDetails => 'Parking';

  @override
  String get poiParkingSurface => 'Surface';

  @override
  String get poiParkingUnderground => 'Underground';

  @override
  String get poiParkingMultiStorey => 'Multi-storey';

  @override
  String get poiParkingStreetSide => 'Street-side';

  @override
  String get poiParkingLane => 'On-street';

  @override
  String get poiParkingRooftop => 'Rooftop';

  @override
  String get poiFee => 'Fee';

  @override
  String get poiFree => 'Free';

  @override
  String get poiPaid => 'Paid';

  @override
  String get poiCapacity => 'Capacity';

  @override
  String get poiMaxStay => 'Maximum stay';

  @override
  String get poiPrice => 'Price';

  @override
  String get poiChargingDetails => 'Charging';

  @override
  String get poiConnectorType2 => 'Type 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiFuelAvailable => 'Fuel available';

  @override
  String get poiDiesel => 'Diesel';

  @override
  String get poiPetrol95 => 'Petrol 95';

  @override
  String get poiSmokingAllowed => 'Smoking allowed';

  @override
  String get poiSmokingOutside => 'Smoking outside';

  @override
  String get poiSmokingAreas => 'Smoking areas';

  @override
  String get poiSmokeFree => 'Smoke-free';

  @override
  String get poiOutdoorSeating => 'Outdoor seating';

  @override
  String get poiTakeaway => 'Takeaway';

  @override
  String get poiTakeawayOnly => 'Takeaway only';

  @override
  String get gpsSignalLost => 'Ztráta signálu GPS';

  @override
  String get poiOpenNow => 'Nyní otevřeno';

  @override
  String get poiClosedNow => 'Zavřeno';

  @override
  String poiOpensAt(String when) {
    return 'Otevírá: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Zavírá: $when';
  }

  @override
  String searchOnEngine(String engine) {
    return 'Hledat na $engine';
  }

  @override
  String get plannerFromHint => 'Odkud…';

  @override
  String get plannerToHint => 'Cíl…';

  @override
  String departEta(String dep, String arr) {
    return 'Odjezd $dep  →  Příjezd $arr';
  }

  @override
  String get modeCar => 'Auto';

  @override
  String get modeBike => 'Kolo';

  @override
  String get modeWalk => 'Pěšky';

  @override
  String windSpeed(String speed) {
    return 'vítr $speed km/h';
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
  String get weatherPartlyCloudy => 'Polojasno';

  @override
  String get weatherCloudy => 'Oblačno';

  @override
  String get weatherFog => 'Mlha';

  @override
  String get weatherLightRain => 'Lehký déšť';

  @override
  String get weatherRain => 'Déšť';

  @override
  String get weatherSnow => 'Sníh';

  @override
  String get weatherShowers => 'Přeháňky';

  @override
  String get weatherThunderstorm => 'Bouřka';

  @override
  String get ztlAheadWarning =>
      'Vpředu je omezená dopravní zóna — trasa jí prochází';

  @override
  String get ztlInsideWarning => 'Omezená dopravní zóna';

  @override
  String get onboardingAppSubtitle => 'Open-source Nostr navigace';

  @override
  String get onboardingWelcomeTitle => 'Vítejte';

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
  String get onboardingGetStarted => 'Začít';

  @override
  String get onboardingNostrTitle => 'Nostr identita';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Připojeno';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (doporučeno)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'Klíč nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Neplatný klíč nsec — zkontrolujte a zkuste znovu.';

  @override
  String get onboardingSkip => 'Přeskočit';

  @override
  String get onboardingContinue => 'Pokračovat';

  @override
  String get onboardingEnterNsec => 'Zadejte klíč nsec';

  @override
  String get onboardingSetupTitle => 'Nastavit Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Poloha';

  @override
  String get onboardingLocationGranted => 'Přístup k poloze povolen';

  @override
  String get onboardingLocationRequired => 'Nutné pro GPS navigaci';

  @override
  String get onboardingGrantButton => 'Povolit';

  @override
  String get onboardingGrapheneTitle => 'Uživatelé GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

  @override
  String get onboardingVoiceTitle => 'Hlasové vedení AI (volitelné)';

  @override
  String get onboardingVoiceReady => 'Hlasový model Kokoro je připraven';

  @override
  String get onboardingVoiceDownloading => 'Stahování hlasového modelu…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Stáhnout';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Jste připraveni navigovat!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Jedeme!';

  @override
  String get onboardingProfileLoading => 'Načítání profilu…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Hlas';

  @override
  String get kokoroVoiceFemale => 'Ženský';

  @override
  String get kokoroVoiceMale => 'Mužský';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Mužský hlas není pro tento jazyk k dispozici';

  @override
  String get kokoroSpeedTitle => 'Rychlost řeči';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Volitelné: uložené oblíbené položky se mohou synchronizovat mezi vašimi zařízeními přes Nostr relé, šifrované end-to-end (NIP-44) vaším vlastním klíčem — relé vidí pouze šifrovaný text a nikdo kromě vás nemůže obsah číst. Aktivujte kdykoli v Nastavení.';

  @override
  String get parkingSaveHere => 'Uložit parkování zde';

  @override
  String get parkingMarkerTitle => 'Místo parkování';

  @override
  String get parkingNavigateHere => 'Navigovat k parkování';

  @override
  String get parkingRemove => 'Odebrat parkování';

  @override
  String get parkingSavedSnack => 'Místo parkování uloženo';

  @override
  String get parkingRemovedSnack => 'Místo parkování odebráno';

  @override
  String get exportFavoritesTitle => 'Exportovat oblíbené';

  @override
  String get exportFavoritesDesc =>
      'Uložte svá oblíbená místa do souboru JSON, který můžete zálohovat nebo přenést na jiné zařízení.';

  @override
  String get exportEncryptToggle => 'Šifrovat heslem';

  @override
  String get exportPasswordHint => 'Heslo';

  @override
  String get exportButton => 'Exportovat';

  @override
  String get exportSuccessSnack => 'Oblíbené byly exportovány';

  @override
  String get exportFailedSnack => 'Export se nezdařil';

  @override
  String get importFavoritesTitle => 'Importovat oblíbené';

  @override
  String get importPasswordPrompt =>
      'Tento soubor je šifrovaný — zadejte heslo';

  @override
  String importSuccessSnack(int n) {
    return 'Importováno $n oblíbených';
  }

  @override
  String get importFailedSnack =>
      'Import se nezdařil — zkontrolujte soubor a heslo';

  @override
  String get syncFavoritesTitle => 'Synchronizace oblíbených';

  @override
  String get syncFavoritesDesc =>
      'Publikujte své oblíbené, šifrované end-to-end, na svá Nostr relé, aby vás následovaly na všech zařízeních. Relé vidí pouze šifrovaný text — nikdo kromě vás nemůže obsah číst.';

  @override
  String get syncNowButton => 'Odeslat na Nostr';

  @override
  String get syncPullButton => 'Stáhnout z Nostr';

  @override
  String get syncPassphraseTitle =>
      'Přístupová fráze synchronizace (volitelná)';

  @override
  String get syncPassphraseDesc =>
      'Druhá vrstva šifrování synchronizovaných oblíbených: i kdyby byl váš klíč Nostr kompromitován, snímek na relayích zůstane bez této fráze nečitelný. Zadáte ji jednou na každém novém zařízení. Ponechte prázdné pro vypnutí.';

  @override
  String get syncSuccessSnack => 'Oblíbené synchronizovány';

  @override
  String get syncFailedSnack => 'Synchronizace se nezdařila';

  @override
  String syncLastSyncLabel(String when) {
    return 'Poslední synchronizace: $when';
  }

  @override
  String get syncNoIdentity =>
      'Přihlaste se přes Nostr (Nastavení → Profil) pro aktivaci synchronizace';

  @override
  String get onboardingVpnNotice =>
      'Pro maximální soukromí — hlášení se šíří sítí Nostr — doporučujeme používat VPN. Doporučenou volbou je Mullvad s platbou v bitcoinech.';

  @override
  String get trafficNormal => 'Běžný provoz';

  @override
  String get trafficModerate => 'Mírný provoz';

  @override
  String get trafficHeavy => 'Hustý provoz';

  @override
  String get avoidHighwaysChip => 'Vyhnout se dálnicím';

  @override
  String get avoidTollsChip => 'Vyhnout se mýtu';

  @override
  String get preferShorterChip => 'Nejkratší trasa';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Zobrazit náhled trasy';
}
