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
  String get autoCenterOnLaunch => 'Käivitusel minu asukohale keskendamine';

  @override
  String get autoCenterOnLaunchDesc =>
      'Kasutab GPS-i automaatselt ainult siis, kui asukohaluba on juba antud';

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

  @override
  String get settingsImperialUnits => 'Angloameerika ühikud';

  @override
  String get settingsImperialUnitsDesc =>
      'Miilid ja jalad kilomeetrite ja meetrite asemel';

  @override
  String get arrivedTitle => '🎉 Olete kohal!';

  @override
  String get arrivedBody => 'Jõudsite oma sihtkohta.';

  @override
  String get arrivedFeedbackPrompt => 'Kuidas läks?';

  @override
  String get feedbackBad => 'Halvasti';

  @override
  String get feedbackGood => 'Hästi!';

  @override
  String get feedbackDialogTitle => 'Rääkige meile, mis läks valesti';

  @override
  String get feedbackHint => 'Kirjeldage probleemi…';

  @override
  String get feedbackSent => 'Tagasiside saadetud — tänan! 🙏';

  @override
  String get feedbackSubmit => 'Saada';

  @override
  String get transportModeCar => 'Auto';

  @override
  String get transportModeWalk => 'Jalgsi';

  @override
  String etaArrivalLabel(String time) {
    return 'Saab. $time';
  }

  @override
  String get supportRoadstr => 'Toeta Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address kopeeritud lõikelauale';
  }

  @override
  String get disclaimerTitle => 'Oluline teade';

  @override
  String get disclaimerAccept => 'Olen lugenud ja nõustun';

  @override
  String get disclaimerBody =>
      'Roadstr on eksperimentaalne, avatud lähtekoodiga ja kogukonna hallatav navigatsioonirakendus, mis põhineb OpenStreetMap andmetel ja Nostr protokollil ning on kasutamiseks kättesaadav igas riigis. Selle rakenduse allalaadimisega, installimisega või kasutamisega nõustub kasutaja tingimusteta kõigi järgnevate tingimustega, ilma territoriaalse piiranguta.\n\n🚗 LIIKLUSOHUTUS ESIKOHAL\nJuht peab alati hoidma pilku teel ja järgima kõiki kohaldatavaid liikluseeskirju ja paigaldatud liiklusmärke, millel on alati eesõigus rakenduse mis tahes juhise ees. Ärge kunagi kasutage seadet sõidu ajal; kinnitage see enne teele asumist heakskiidetud, nähtavasse hoidikusse ja ärge kunagi pöörake tähelepanu teelt kõrvale, et sellega suhelda, kui sõiduk liigub.\n\n⚠️ RISKI ENDALE VÕTMINE — KOGU MAAILMAS\nKasutades Roadstr rakendust, mis tahes riigis ja mis tahes õigussüsteemi alusel, võtab kasutaja teadlikult ja vabatahtlikult enda kanda KÕIK selle kasutamisega seotud riskid, sealhulgas, kuid mitte ainult: liiklusõnnetused, kehavigastused, surm, varakahju, sõiduki kahjustumine, trahvid, haldussanktsioonid, teisaldamine, sõiduki konfiskeerimine, kriminaalvastutus või mis tahes muu tagajärg, mis tuleneb otseselt või kaudselt rakendusele tuginemisest. Kasutaja kannab ainuisikuliselt täieliku vastutuse iga sõidu- ja navigatsiooniotsuse eest.\n\n🚫 GARANTII PUUDUMINE\nRoadstr rakendus antakse kasutusse rangelt põhimõttel „NAGU ON” ja „VASTAVALT KÄTTESAADAVUSELE”, ilma igasuguse garantiita, olgu see selgesõnaline, kaudne või seadusest tulenev — sealhulgas, kuid mitte ainult, täpsuse, täielikkuse, usaldusväärsuse, kättesaadavuse, kaubastatavuse, konkreetseks otstarbeks sobivuse ja õiguste rikkumatuse garantiid. Kaardiandmed, marsruudi koostamine, kiirusepiirangud, kiiruskaamerad ja piiratud liiklusega tsoonide (ZTL/ZAC/LTZ) teave pärinevad avatud, kogukonna poolt hallatavatest allikatest (OpenStreetMap, Overpass API), mis võivad mis tahes riigi, piirkonna või omavalitsuse puhul olla igal ajal ja ette teatamata puudulikud, aegunud või ebatäpsed. Kasutaja vastutab ainuisikuliselt selle eest, et enne reisi ja reisi ajal iseseisvalt kontrollida mis tahes soovitatud marsruudi seaduslikkust ja läbitavust ametliku kohaliku liiklusmärgistuse ja eeskirjade alusel.\n\n📍 TÄPSUS JA GPS\nGPS-positsioneerimine võib olla ebatäpne või kättesaamatu. Kõik juhised, vahemaad ja hoiatused on esitatud üksnes suunavaks abiks ja neile ei tohi kunagi tugineda kui ainsale alusele sõiduotsuse tegemisel.\n\n🛡️ VASTUTUSE PIIRAMINE\nKohaldatava õigusega lubatud maksimaalses ulatuses mis tahes jurisdiktsioonis ei vastuta arendajad, kaastöölised ega ükski Roadstr rakenduse loomises või levitamises osalenud pool otseste, kaudsete, juhuslike, tulenevate, eriliste, näitlike ega karistuslike kahjude eest mis tahes liiki — sealhulgas kehavigastuste, surma või rahalise kahju eest —, mis tulenevad rakenduse kasutamisest või kasutamise võimatusest või on sellega seotud, isegi kui neid on teavitatud selliste kahjude tekkimise võimalusest. Kui mõni jurisdiktsioon ei luba osa või kogu käesolevat piirangut, piirdub vastutus väikseima ulatusega, mida selle jurisdiktsiooni õigus lubab.\n\n🤝 KAHJU HÜVITAMINE\nKasutaja nõustub hüvitama arendajatele ja kaastöölistele ning vabastama nad vastutusest mis tahes nõude, kahju, kaotuse või kulu eest (sealhulgas õigusabikulud), mis tuleneb kasutaja poolt rakenduse kasutamisest või käesolevate tingimuste rikkumisest.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ SÄTETE LAHUTATAVUS\nKui mõni käesolevate tingimuste säte osutub mingis jurisdiktsioonis kehtetuks, piiratakse või eraldatakse see säte minimaalselt vajalikus ulatuses ning kõik ülejäänud sätted jäävad täies ulatuses kehtima.\n\nKasutades Roadstr rakendust kus tahes maailmas, kinnitab kasutaja, et on lugenud, mõistnud ja tingimusteta nõustunud iga eespool nimetatud tingimusega, ning võtab enda kanda täieliku ja tervikliku vastutuse — ning kogu riski — rakenduse kasutamise ja sellest tuleneva mis tahes tagajärje eest.';

  @override
  String get readOnWikipedia => 'Loe Vikipeediast';

  @override
  String get openInBrowser => 'Ava brauseris';

  @override
  String get wikipediaLoadFailed => 'Wikipediat ei saanud turvaliselt laadida.';

  @override
  String get retry => 'Proovi uuesti';

  @override
  String get poiDetailsFromOsm => 'Teave OpenStreetMapist';

  @override
  String get poiCategory => 'Kategooria';

  @override
  String get poiOperator => 'Operaator';

  @override
  String get poiCuisine => 'Köök';

  @override
  String get poiAccessibility => 'Ligipääsetavus';

  @override
  String get poiWheelchairYes => 'Ratastooliga ligipääsetav';

  @override
  String get poiWheelchairLimited => 'Piiratud ligipääs ratastooliga';

  @override
  String get poiWheelchairNo => 'Ratastooliga ligipääs puudub';

  @override
  String get poiContact => 'Kontakt';

  @override
  String get poiAddress => 'Aadress';

  @override
  String get poiWebsite => 'Veebisait';

  @override
  String get poiAccessPrivate => 'Privaatne juurdepääs';

  @override
  String get poiAccessCustomers => 'Ainult klientidele';

  @override
  String get poiAccessPermit => 'Luba nõutav';

  @override
  String get poiAccessNo => 'Avalik juurdepääs puudub';

  @override
  String get poiAccessDestination => 'Juurdepääs ainult sihtkohta';

  @override
  String get poiLightningAccepted => 'Lightning on aktsepteeritud';

  @override
  String get poiBitcoinAccepted => 'Bitcoin on aktsepteeritud';

  @override
  String get poiParkingDetails => 'Parkimine';

  @override
  String get poiParkingSurface => 'Maapealne';

  @override
  String get poiParkingUnderground => 'Maa-alune';

  @override
  String get poiParkingMultiStorey => 'Mitmekorruseline';

  @override
  String get poiParkingStreetSide => 'Tänava ääres';

  @override
  String get poiParkingLane => 'Tänaval';

  @override
  String get poiParkingRooftop => 'Katusel';

  @override
  String get poiFee => 'Tasu';

  @override
  String get poiFree => 'Tasuta';

  @override
  String get poiPaid => 'Tasuline';

  @override
  String get poiCapacity => 'Mahutavus';

  @override
  String get poiMaxStay => 'Maksimaalne parkimisaeg';

  @override
  String get poiPrice => 'Hind';

  @override
  String get poiChargingDetails => 'Laadimine';

  @override
  String get poiConnectorType2 => 'Tüüp 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Diisel';

  @override
  String get poiPetrol95 => 'Bensiin 95';

  @override
  String get poiSmokingAllowed => 'Suitsetamine lubatud';

  @override
  String get poiSmokingOutside => 'Suitsetamine väljas';

  @override
  String get poiSmokingAreas => 'Suitsetamisalad';

  @override
  String get poiSmokeFree => 'Suitsuvaba';

  @override
  String get poiOutdoorSeating => 'Väljas istumiskohad';

  @override
  String get poiTakeaway => 'Kaasavõtt';

  @override
  String get poiTakeawayOnly => 'Ainult kaasavõtt';

  @override
  String get gpsSignalLost => 'GPS-signaal kadus';

  @override
  String get poiOpenNow => 'Praegu avatud';

  @override
  String get poiClosedNow => 'Suletud';

  @override
  String poiOpensAt(String when) {
    return 'Avaneb: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Sulgub: $when';
  }

  @override
  String searchOnEngine(String engine) {
    return 'Otsi $engine';
  }

  @override
  String get plannerFromHint => 'Kust…';

  @override
  String get plannerToHint => 'Sihtkoht…';

  @override
  String departEta(String dep, String arr) {
    return 'Väljumine $dep  →  Saabumine $arr';
  }

  @override
  String get modeCar => 'Auto';

  @override
  String get modeBike => 'Jalgratas';

  @override
  String get modeWalk => 'Jalgsi';

  @override
  String windSpeed(String speed) {
    return 'tuul $speed km/h';
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
  String get weatherClear => 'Selge';

  @override
  String get weatherPartlyCloudy => 'Poolpilves';

  @override
  String get weatherCloudy => 'Pilves';

  @override
  String get weatherFog => 'Udu';

  @override
  String get weatherLightRain => 'Vihmasadu';

  @override
  String get weatherRain => 'Vihm';

  @override
  String get weatherSnow => 'Lumi';

  @override
  String get weatherShowers => 'Hoogsadu';

  @override
  String get weatherThunderstorm => 'Äike';

  @override
  String get ztlAheadWarning =>
      'Ees on piiratud liikluse tsoon — marsruut läbib seda';

  @override
  String get ztlInsideWarning => 'Piiratud liikluse tsoon';

  @override
  String get onboardingAppSubtitle => 'Avatud lähtekoodiga Nostr navigatsioon';

  @override
  String get onboardingWelcomeTitle => 'Tere tulemast';

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
  String get onboardingGetStarted => 'Alusta';

  @override
  String get onboardingNostrTitle => 'Nostr identiteet';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Ühendatud';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (soovitatav)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec võti';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Vigane nsec võti — kontrollige ja proovige uuesti.';

  @override
  String get onboardingSkip => 'Jäta praegu vahele';

  @override
  String get onboardingContinue => 'Jätka';

  @override
  String get onboardingEnterNsec => 'Sisestage nsec võti';

  @override
  String get onboardingSetupTitle => 'Seadista Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Asukoht';

  @override
  String get onboardingLocationGranted => 'Asukoha juurdepääs antud';

  @override
  String get onboardingLocationRequired => 'Nõutav GPS navigatsiooniks';

  @override
  String get onboardingGrantButton => 'Anna luba';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS kasutajad';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

  @override
  String get onboardingVoiceTitle => 'AI hääljuhtimine (valikuline)';

  @override
  String get onboardingVoiceReady => 'Kokoro häälemudel on valmis';

  @override
  String get onboardingVoiceDownloading => 'Häälemudeli allalaadimine…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Laadi alla';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Olete valmis navigeerima!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Lähme!';

  @override
  String get onboardingProfileLoading => 'Profiili laadimine…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Hääl';

  @override
  String get kokoroVoiceFemale => 'Naishääl';

  @override
  String get kokoroVoiceMale => 'Meeshääl';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Meeshääl pole selle keele jaoks saadaval';

  @override
  String get kokoroSpeedTitle => 'Kõne kiirus';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Valikuline: salvestatud lemmikud saavad sünkroonida sinu seadmete vahel Nostr releede kaudu, otspunktkrüpteeritult (NIP-44) sinu enda võtmega — releed näevad ainult šifreeritud teksti ja keegi peale sinu ei saa sisu lugeda. Luba igal ajal Seadetest.';

  @override
  String get parkingSaveHere => 'Salvesta parkimine siia';

  @override
  String get parkingMarkerTitle => 'Parkimiskoht';

  @override
  String get parkingNavigateHere => 'Navigeeri parkimiskohta';

  @override
  String get parkingRemove => 'Eemalda parkimine';

  @override
  String get parkingSavedSnack => 'Parkimiskoht salvestatud';

  @override
  String get parkingRemovedSnack => 'Parkimiskoht eemaldatud';

  @override
  String get exportFavoritesTitle => 'Ekspordi lemmikud';

  @override
  String get exportFavoritesDesc =>
      'Salvesta oma lemmikkohad JSON-faili, mida saad varundada või teise seadmesse üle kanda.';

  @override
  String get exportEncryptToggle => 'Krüpteeri parooliga';

  @override
  String get exportPasswordHint => 'Parool';

  @override
  String get exportButton => 'Ekspordi';

  @override
  String get exportSuccessSnack => 'Lemmikud eksporditud';

  @override
  String get exportFailedSnack => 'Eksport ebaõnnestus';

  @override
  String get importFavoritesTitle => 'Impordi lemmikud';

  @override
  String get importPasswordPrompt =>
      'See fail on krüpteeritud — sisesta parool';

  @override
  String importSuccessSnack(int n) {
    return '$n lemmikut imporditud';
  }

  @override
  String get importFailedSnack =>
      'Import ebaõnnestus — kontrolli faili ja parooli';

  @override
  String get syncFavoritesTitle => 'Sünkrooni lemmikud';

  @override
  String get syncFavoritesDesc =>
      'Avalda oma lemmikud otspunktkrüpteeritult oma Nostr releedele, et need järgneksid sulle kõigis seadmetes. Releed näevad ainult šifreeritud teksti — keegi peale sinu ei saa sisu lugeda.';

  @override
  String get syncNowButton => 'Saada Nostrisse';

  @override
  String get syncPullButton => 'Too Nostrist';

  @override
  String get syncPassphraseTitle => 'Sünkroonimise paroolifraas (valikuline)';

  @override
  String get syncPassphraseDesc =>
      'Sünkroonitud lemmikute teine krüpteerimiskiht: isegi kui teie Nostr-võti satuks ohtu, jääb hetktõmmis releedel ilma selle fraasita loetamatuks. Sisestate selle üks kord igas uues seadmes. Keelamiseks jätke tühjaks.';

  @override
  String get syncSuccessSnack => 'Lemmikud sünkroonitud';

  @override
  String get syncFailedSnack => 'Sünkroonimine ebaõnnestus';

  @override
  String syncLastSyncLabel(String when) {
    return 'Viimati sünkroonitud: $when';
  }

  @override
  String get syncNoIdentity =>
      'Sünkroonimise lubamiseks logi sisse Nostriga (Seaded → Profiil)';

  @override
  String get onboardingVpnNotice =>
      'Maksimaalse privaatsuse tagamiseks — teated levivad Nostr-võrgus — on soovitatav kasutada VPN-i. Soovitatud valik on Mullvad, mille eest saab maksta Bitcoiniga.';

  @override
  String get trafficNormal => 'Tavaline liiklus';

  @override
  String get trafficModerate => 'Mõõdukas liiklus';

  @override
  String get trafficHeavy => 'Tihe liiklus';

  @override
  String get avoidHighwaysChip => 'Väldi kiirteid';

  @override
  String get avoidTollsChip => 'Väldi teemakse';

  @override
  String get preferShorterChip => 'Lühim marsruut';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Kuva marsruudi eelvaade';

  @override
  String get avoidHighwaysAndTolls => 'Väldi kiirteid ja tasulisi teid';

  @override
  String get avoidRouteUnavailable =>
      'Marsruuti, mis väldiks nii kiirteid kui ka tasulisi teid, ei leitud.';

  @override
  String get avoidanceUnavoidableSection =>
      'Vähendab kiirteid/tasulisi teid · vältimatu lõik';
}
