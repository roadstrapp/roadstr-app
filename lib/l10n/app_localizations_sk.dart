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
  String get settingsImperialUnits => 'Imperiálne jednotky';

  @override
  String get settingsImperialUnitsDesc =>
      'Míle a stopy namiesto kilometrov a metrov';

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
      'Roadstr je experimentálna, open-source, komunitou spravovaná navigačná aplikácia založená na dátach OpenStreetMap a protokole Nostr, sprístupnená na použitie v ktorejkoľvek krajine. Stiahnutím, inštaláciou alebo používaním tejto aplikácie používateľ bezpodmienečne prijíma všetky nasledujúce podmienky, bez územného obmedzenia.\n\n🚗 BEZPEČNOSŤ CESTNEJ PREMÁVKY NA PRVOM MIESTE\nVodič musí vždy sledovať cestu a dodržiavať všetky platné dopravné predpisy a umiestnené dopravné značenie, ktoré má vždy prednosť pred akýmkoľvek pokynom aplikácie. Nikdy neobsluhujte zariadenie počas jazdy; pred vyrazením na cestu ho upevnite v schválenom, viditeľnom držiaku a počas jazdy vozidla nikdy neodkláňajte pozornosť od cesty kvôli interakcii s ním.\n\n⚠️ PREVZATIE RIZIKA — CELOSVETOVO\nPoužívaním aplikácie Roadstr, v ktorejkoľvek krajine a podľa akéhokoľvek právneho systému, používateľ vedome a dobrovoľne preberá VŠETKY riziká spojené s jej používaním, vrátane, ale nie výlučne: dopravných nehôd, ublíženia na zdraví, úmrtia, škody na majetku, poškodenia vozidla, pokút, správnych sankcií, odtiahnutia vozidla, jeho zaistenia, trestnoprávnej zodpovednosti alebo akýchkoľvek iných následkov vyplývajúcich priamo či nepriamo zo spoliehania sa na aplikáciu. Používateľ sám nesie plnú zodpovednosť za každé rozhodnutie súvisiace s vedením vozidla a navigáciou.\n\n🚫 ŽIADNA ZÁRUKA\nRoadstr sa poskytuje výlučne „TAK, AKO JE“ a „PODĽA DOSTUPNOSTI“, bez akejkoľvek záruky, či už výslovnej, implicitnej alebo zákonnej — vrátane, bez obmedzenia, záruk presnosti, úplnosti, spoľahlivosti, dostupnosti, predajnosti, vhodnosti na konkrétny účel a neporušovania práv. Mapové dáta, plánovanie trasy, rýchlostné limity, informácie o rýchlostných kamerách a zónach s obmedzenou dopravou (ZTL/ZAC/LTZ) pochádzajú z otvorených, komunitou spravovaných zdrojov (OpenStreetMap, Overpass API), ktoré môžu byť neúplné, zastarané alebo nepresné pre ktorúkoľvek krajinu, región alebo obec, kedykoľvek a bez upozornenia. Používateľ je výlučne zodpovedný za to, aby si pred cestou a počas nej samostatne overil zákonnosť a priechodnosť akejkoľvek navrhovanej trasy porovnaním s oficiálnym miestnym dopravným značením a predpismi.\n\n📍 PRESNOSŤ A GPS\nUrčovanie polohy prostredníctvom GPS môže byť nepresné alebo nedostupné. Všetky pokyny, vzdialenosti a upozornenia sú poskytované len na orientačné účely a nikdy by sa nemali považovať za jediný podklad pre rozhodnutie o vedení vozidla.\n\n🛡️ OBMEDZENIE ZODPOVEDNOSTI\nV najväčšom rozsahu povolenom platným právom v ktorejkoľvek jurisdikcii, vývojári, prispievatelia a akákoľvek strana zúčastnená na tvorbe alebo distribúcii aplikácie Roadstr nenesú zodpovednosť za žiadne priame, nepriame, náhodné, následné, osobitné, exemplárne alebo represívne škody akéhokoľvek druhu — vrátane ublíženia na zdraví, úmrtia alebo finančnej straty — vyplývajúce z používania alebo nemožnosti používať aplikáciu alebo s tým súvisiace, a to aj v prípade, že boli upozornení na možnosť vzniku takýchto škôd. Ak niektorá jurisdikcia neumožňuje čiastočne alebo úplne uplatniť toto obmedzenie, zodpovednosť sa obmedzuje na najmenší rozsah povolený právom danej jurisdikcie.\n\n🤝 NÁHRADA ŠKODY\nPoužívateľ súhlasí s tým, že odškodní a ochráni vývojárov a prispievateľov pred akýmikoľvek nárokmi, škodami, stratami alebo výdavkami (vrátane trov právneho zastúpenia) vyplývajúcimi z používania aplikácie používateľom alebo z porušenia týchto podmienok.\n\n🔒 OCHRANA SÚKROMIA\nŽiadne údaje o polohe sa neprenášajú na vlastné servery Roadstr. Výpočet trasy sa vykonáva prostredníctvom služieb tretích strán (OSRM, GraphHopper, OpenRouteService), ktorým sa odosielajú iba súradnice východiskového bodu a cieľa.\n\n⚖️ ODDELITEĽNOSŤ\nAk sa niektoré ustanovenie týchto podmienok v danej jurisdikcii ukáže ako nevymáhateľné, toto ustanovenie sa obmedzí alebo vypustí v minimálnom nevyhnutnom rozsahu a všetky ostatné ustanovenia zostávajú v plnej platnosti a účinnosti.\n\nPoužívaním aplikácie Roadstr, kdekoľvek na svete, používateľ potvrdzuje, že si prečítal, porozumel a bezpodmienečne prijal všetky vyššie uvedené podmienky, a preberá úplnú a výhradnú zodpovednosť — ako aj všetky riziká — za používanie aplikácie a akékoľvek z toho vyplývajúce následky.';

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

  @override
  String get modeCar => 'Auto';

  @override
  String get modeBike => 'Bicykel';

  @override
  String get modeWalk => 'Pešo';

  @override
  String windSpeed(String speed) {
    return 'vietor $speed km/h';
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
  String get weatherFog => 'Hmla';

  @override
  String get weatherLightRain => 'Slabý dážď';

  @override
  String get weatherRain => 'Dážď';

  @override
  String get weatherSnow => 'Sneh';

  @override
  String get weatherShowers => 'Prehánky';

  @override
  String get weatherThunderstorm => 'Búrka';

  @override
  String get ztlAheadWarning =>
      'Vpredu je zóna s obmedzenou dopravou — trasa ňou prechádza';

  @override
  String get ztlInsideWarning => 'Obmedzená dopravná zóna';

  @override
  String get onboardingAppSubtitle => 'Open-source Nostr navigácia';

  @override
  String get onboardingWelcomeTitle => 'Vitajte';

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
  String get onboardingGetStarted => 'Začať';

  @override
  String get onboardingNostrTitle => 'Nostr identita';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Pripojené';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (odporúčané)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'Kľúč nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Neplatný kľúč nsec — skontrolujte a skúste znova.';

  @override
  String get onboardingSkip => 'Preskočiť teraz';

  @override
  String get onboardingContinue => 'Pokračovať';

  @override
  String get onboardingEnterNsec => 'Zadajte kľúč nsec';

  @override
  String get onboardingSetupTitle => 'Nastaviť Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Poloha';

  @override
  String get onboardingLocationGranted => 'Prístup k polohe udelený';

  @override
  String get onboardingLocationRequired => 'Potrebné pre GPS navigáciu';

  @override
  String get onboardingGrantButton => 'Povoliť';

  @override
  String get onboardingGrapheneTitle => 'Používatelia GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'Hlasové navádzanie AI (voliteľné)';

  @override
  String get onboardingVoiceReady => 'Hlasový model Kokoro je pripravený';

  @override
  String get onboardingVoiceDownloading => 'Sťahovanie hlasového modelu…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Stiahnuť';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Ste pripravení navigovať!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Poďme!';

  @override
  String get onboardingProfileLoading => 'Načítava sa profil…';

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
      'Mužský hlas nie je pre tento jazyk dostupný';

  @override
  String get kokoroSpeedTitle => 'Rýchlosť reči';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Voliteľné: uložené obľúbené sa môžu synchronizovať medzi vašimi zariadeniami cez Nostr relé, šifrované end-to-end (NIP-44) vaším vlastným kľúčom — relé vidia iba šifrovaný text a nikto okrem vás nemôže obsah čítať. Aktivujte kedykoľvek v Nastaveniach.';

  @override
  String get parkingSaveHere => 'Uložiť parkovanie tu';

  @override
  String get parkingMarkerTitle => 'Parkovacie miesto';

  @override
  String get parkingNavigateHere => 'Navigovať k parkovaniu';

  @override
  String get parkingRemove => 'Odstrániť parkovanie';

  @override
  String get parkingSavedSnack => 'Parkovacie miesto uložené';

  @override
  String get parkingRemovedSnack => 'Parkovacie miesto odstránené';

  @override
  String get exportFavoritesTitle => 'Exportovať obľúbené';

  @override
  String get exportFavoritesDesc =>
      'Uložte si svoje obľúbené miesta do súboru JSON, ktorý môžete zálohovať alebo preniesť na iné zariadenie.';

  @override
  String get exportEncryptToggle => 'Šifrovať heslom';

  @override
  String get exportPasswordHint => 'Heslo';

  @override
  String get exportButton => 'Exportovať';

  @override
  String get exportSuccessSnack => 'Obľúbené boli exportované';

  @override
  String get exportFailedSnack => 'Export zlyhal';

  @override
  String get importFavoritesTitle => 'Importovať obľúbené';

  @override
  String get importPasswordPrompt => 'Tento súbor je šifrovaný — zadajte heslo';

  @override
  String importSuccessSnack(int n) {
    return 'Importovaných $n obľúbených';
  }

  @override
  String get importFailedSnack => 'Import zlyhal — skontrolujte súbor a heslo';

  @override
  String get syncFavoritesTitle => 'Synchronizovať obľúbené';

  @override
  String get syncFavoritesDesc =>
      'Publikujte svoje obľúbené, šifrované end-to-end, na svoje Nostr relé, aby vás nasledovali na všetkých zariadeniach. Relé vidia iba šifrovaný text — nikto okrem vás nemôže obsah čítať.';

  @override
  String get syncNowButton => 'Odoslať na Nostr';

  @override
  String get syncPullButton => 'Stiahnuť z Nostr';

  @override
  String get syncSuccessSnack => 'Obľúbené synchronizované';

  @override
  String get syncFailedSnack => 'Synchronizácia zlyhala';

  @override
  String syncLastSyncLabel(String when) {
    return 'Naposledy synchronizované: $when';
  }

  @override
  String get syncNoIdentity =>
      'Prihláste sa cez Nostr (Nastavenia → Profil) na aktiváciu synchronizácie';

  @override
  String get onboardingVpnNotice =>
      'Pre maximálne súkromie — hlásenia sa šíria sieťou Nostr — odporúčame používať VPN. Odporúčanou voľbou je Mullvad s platbou v bitcoinoch.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Pre spoľahlivú prevádzku nastavte povolenie polohy na „Povoliť vždy“, nie iba počas používania aplikácie.';

  @override
  String get trafficNormal => 'Bežná premávka';

  @override
  String get trafficModerate => 'Mierna premávka';

  @override
  String get trafficHeavy => 'Hustá premávka';

  @override
  String get avoidHighwaysChip => 'Vyhnúť sa diaľniciam';

  @override
  String get avoidTollsChip => 'Vyhnúť sa mýtu';

  @override
  String get preferShorterChip => 'Najkratšia trasa';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Zobraziť náhľad trasy';
}
