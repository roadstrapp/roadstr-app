// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Finnish (`fi`).
class AppLocalizationsFi extends AppLocalizations {
  AppLocalizationsFi([String locale = 'fi']) : super(locale);

  @override
  String get searchHint => 'Minne haluatte mennä?';

  @override
  String get calculatingRoute => 'Lasketaan reittiä…';

  @override
  String get routeNotFoundTitle => 'Reittiä ei löydy';

  @override
  String get noRouteFound => 'Reittiä ei löydy. Tarkista yhteys.';

  @override
  String get emptyServerResponse =>
      'Tyhjä vastaus palvelimelta. Tarkista yhteys.';

  @override
  String routeError(String error) {
    return 'Reittilaskentavirhe: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS ei saatavilla — Asetukset → Sovellus → Roadstr → Käyttöoikeudet → Sijainti';

  @override
  String get acquiringGps => 'Haetaan GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopper-palvelinta ei ole määritetty — käytetään OSRM:ää';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper API-avainta ei ole määritetty — käytetään OSRM:ää';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService API-avainta ei ole määritetty — käytetään OSRM:ää';

  @override
  String get chooseRoute => 'Valitse reitti';

  @override
  String routeOptionsCount(int count) {
    return '$count vaihtoehtoa';
  }

  @override
  String get cancel => 'Peruuta';

  @override
  String get startNavigation => 'Aloita navigointi';

  @override
  String get fastestRoute => 'Nopein';

  @override
  String get now => 'Nyt';

  @override
  String get history => 'Historia';

  @override
  String get clearHistory => 'Tyhjennä';

  @override
  String get selectedPosition => 'Valittu sijainti';

  @override
  String get bottomBarProfile => 'Profiili';

  @override
  String get bottomBarMenu => 'Valikko';

  @override
  String get settingsTitle => 'Asetukset';

  @override
  String get sectionTheme => 'Teema';

  @override
  String get sectionMap => 'Kartta';

  @override
  String get sectionInfo => 'Tiedot';

  @override
  String get sectionLanguage => 'Kieli';

  @override
  String get themeLightNostr => 'Vaalea · Nostr Violetti';

  @override
  String get themeLightBitcoin => 'Vaalea · Bitcoin Oranssi';

  @override
  String get themeDarkNostr => 'Tumma · Nostr Violetti';

  @override
  String get themeDarkBitcoin => 'Tumma · Bitcoin Oranssi';

  @override
  String get langSystem => 'Järjestelmän oletus';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Pidä näyttö päällä';

  @override
  String get keepScreenOnDescription =>
      'Estää laitteen siirtymisen lepotilaan navigoinnin aikana';

  @override
  String get rotateMap => 'Kartta seuraa suuntaa';

  @override
  String get rotateMapDescription => 'Kartta pyörii ajosuunnan mukaan';

  @override
  String get mapTileUrlLabel => 'Karttalaattojen URL';

  @override
  String get routingProviderLabel => 'Reitityspalveluntarjoaja';

  @override
  String get osrmProvider => 'OSRM (julkinen, ei avain tarvita)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (paikallinen/yksityinen)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API-avain)';

  @override
  String get openrouteProvider => 'OpenRouteService (API-avain)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API-avain (valinnainen)';

  @override
  String get verify => 'Vahvista';

  @override
  String get graphhopperServerUrlRequired =>
      'Syötä palvelimen URL ennen vahvistamista.';

  @override
  String get successTitle => 'Onnistui';

  @override
  String get graphhopperServerReachable =>
      'GraphHopper-palvelin tavoitettavissa';

  @override
  String get errorTitle => 'Virhe';

  @override
  String get close => 'Sulje';

  @override
  String get infoVersion => 'Versio';

  @override
  String get infoProtocol => 'Protokolla';

  @override
  String get infoMaps => 'Kartat';

  @override
  String get infoRouting => 'Reititys';

  @override
  String get infoSource => 'Lähde';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (itse isännöity)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (pilvi)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profiili';

  @override
  String get notConnected => 'Ei yhdistetty';

  @override
  String get loginWithNostrTitle => 'KIRJAUDU NOSTR-TILILLÄ';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Yksityinen avain ei koskaan poistu laitteeltasi · Suositeltu';

  @override
  String get nsecLoginOption => 'Lisää nsec';

  @override
  String get nsecLoginDescription =>
      'Yksityinen avain tallennettu paikallisesti · Vähemmän turvallinen';

  @override
  String get connectedViaAmber => 'Yhdistetty Amberin kautta (NIP-55)';

  @override
  String get connectedViaNsec => 'Yhdistetty nseciä käyttäen';

  @override
  String get publicKeyLabel => 'JULKINEN AVAIN';

  @override
  String get npubCopiedToClipboard => 'npub kopioitu leikepöydälle';

  @override
  String get logoutTitle => 'Katkaise yhteys';

  @override
  String get logoutConfirmation =>
      'Poistetaanko Nostr-tunnistetiedot tältä laitteelta?';

  @override
  String get logoutButton => 'Katkaise yhteys';

  @override
  String get nostrIdentityInfo =>
      'Nostr-identiteetillä voit julkaista liikennevaroituksia, raportteja ja mielenkiintoisia kohteita hajautetusti Nostr-verkossa ilman keskitettyjä palvelimia.';

  @override
  String get warningTitle => 'Varoitus';

  @override
  String get nsecWarning =>
      'Olet aikeissa syöttää Nostr-yksityisen avaimesi suoraan sovellukseen. Kuka tahansa, jolla on fyysinen tai etäkäyttö laitteeseesi, voi lukea sen ja ottaa pysyvän hallinnan Nostr-identiteetistäsi.';

  @override
  String get amberSecureMethodHint =>
      '✦  Turvallinen menetelmä on Amber (NIP-55): nsec ei koskaan poistu sovelluksen allekirjoittajan holvista.';

  @override
  String get nsecRiskAcknowledgment => 'Ymmärrän riskin ja haluan jatkaa silti';

  @override
  String get continueButton => 'Jatka';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber on NIP-55-yhteensopiva Android-sovelluksen allekirjoittaja. Yksityinen avaimesi pysyy eristettynä Amberissa eikä sitä koskaan jaeta.';

  @override
  String get requestKeyFromAmber => 'Pyydä julkinen avain Amberilta';

  @override
  String get amberNotFound =>
      'Amberia ei löydy. Asenna se Play Kaupasta tai syötä npub manuaalisesti.';

  @override
  String get waitingForAmberResponse => 'Odotetaan Amberin vastausta…';

  @override
  String get pasteNpubManually => 'Liitä npub manuaalisesti:';

  @override
  String get confirmNpub => 'Vahvista npub';

  @override
  String get enterNsecTitle => 'Lisää nsec';

  @override
  String get loginButton => 'Kirjaudu';

  @override
  String get invalidNpubTitle => 'Virheellinen npub';

  @override
  String get invalidNsecTitle => 'Virheellinen nsec';

  @override
  String get invalidNpubMessage => 'Varmista, että liitit oikean npub:n.';

  @override
  String get invalidNsecMessage => 'Varmista, että liitit oikean nsec:n.';

  @override
  String get amberResponseError => 'Amber-vastausvirhe';

  @override
  String get ok => 'OK';

  @override
  String get or => 'tai';

  @override
  String get gpsNotActiveTitle => 'GPS ei ole aktiivinen';

  @override
  String get gpsDisabledMessage =>
      'Ota GPS käyttöön laitteen asetuksissa sijaintisi selvittämiseksi ja navigoinnin käyttämiseksi.';

  @override
  String get openSettings => 'Asetukset';

  @override
  String get myLocation => 'Sijaintini';

  @override
  String get loginToReport =>
      'Kirjaudu Nostrilla (Profiili-osio) raportoidaksesi tapahtumia';

  @override
  String get navigateHere => 'Navigoi tänne';

  @override
  String get reportEventHere => 'Ilmoita tapahtuma tässä';

  @override
  String get zapSendSats => 'Zap ⚡ (lähetä sateja)';

  @override
  String get sendZap => 'Lähetä Zap';

  @override
  String get chooseAmountSats => 'Valitse summa satosheina (sats):';

  @override
  String get customAmount => 'Mukautettu summa…';

  @override
  String get zapSending => 'Lähetetään…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Haetaan Lightning-osoitetta…';

  @override
  String get noLightningAddress =>
      'Tällä raportoijalla ei ole Lightning-osoitetta';

  @override
  String get requestingInvoice => 'Pyydetään laskua…';

  @override
  String get lnurlUnavailable => 'LNURL ei saatavilla';

  @override
  String get invoiceFailed => 'Laskun luominen epäonnistui';

  @override
  String get openingWallet => 'Avataan lompakko…';

  @override
  String get payingViaNwc => 'Maksetaan NWC:n kautta…';

  @override
  String get noLightningWallet => 'Lightning-lompakkoa ei löydy';

  @override
  String zapSent(int sats) {
    return '⚡ $sats satsia lähetetty!';
  }

  @override
  String get stillThere => 'Yhä siellä';

  @override
  String get notThereAnymore => 'Ei enää siellä';

  @override
  String get loginToConfirm =>
      'Kirjaudu Nostrilla vahvistaaksesi tai kiistääksesi';

  @override
  String get reportAnEvent => 'Ilmoita tapahtuma';

  @override
  String get optionalComment => 'Valinnainen kommentti…';

  @override
  String get publish => 'Julkaise';

  @override
  String get publishing => 'Julkaistaan…';

  @override
  String get reportPublished => 'Raportti julkaistu ✓';

  @override
  String get myReports => 'OMAT RAPORTTINI';

  @override
  String get noReportsYet => 'Ei julkaistuja raportteja';

  @override
  String get zapBalance => 'Zap-saldo';

  @override
  String get satoshiFromReports => 'Raporteistasi saadut satoshit';

  @override
  String get reputationHigh => 'Korkea';

  @override
  String get reputationMedium => 'Keskitaso';

  @override
  String get reputationLow => 'Matala';

  @override
  String reputationLabel(String level) {
    return 'Maine $level';
  }

  @override
  String reliability(int pct) {
    return 'Luotettavuus: $pct%';
  }

  @override
  String get confirmedLabel => 'Vahvistettu';

  @override
  String get removedLabel => 'Poistettu';

  @override
  String get positionLabel => 'Sijainti';

  @override
  String get loadingLabel => 'Ladataan…';

  @override
  String get sectionWebSearch => 'Verkkohaku';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Liitä NWC-URI (Alby Hub, Mutiny, Cashu…) maksaaksesi Zapeja suoraan sovelluksesta.';

  @override
  String get searchEngineQwantDesc =>
      'Eurooppalainen, yksityisyys ensin. Ei seurantaa, ei mainosprofiilejoa. Suositeltu.';

  @override
  String get searchEngineBraveDesc =>
      'Riippumaton indeksi, avoimen lähdekoodin. Ei Google- tai Bing-riippuvuutta. Nolla profiloinnin.';

  @override
  String get searchEngineDdgDesc =>
      'Yksityisyyspainotteinen ja suosittu. Tulokset osittain Bingiltä — pidä se mielessä.';

  @override
  String get searchEngineStartpageDesc =>
      'Google-tulokset ilman tietojesi luovuttamista Googlelle. Kohtuullinen kompromissi.';

  @override
  String get searchEngineGoogleDesc =>
      'Erittäin tehokas. Mutta muista: Google tuntee sinut paremmin kuin äitisi ja myy kaiken mainostajille. Sinun valintasi. 🍪';

  @override
  String get categoryPolice => 'Poliisi';

  @override
  String get categorySpeedCamera => 'Nopeusvalvontakamera';

  @override
  String get categoryTrafficJam => 'Ruuhka';

  @override
  String get categoryAccident => 'Onnettomuus';

  @override
  String get categoryRoadClosure => 'Tiesulku';

  @override
  String get categoryConstruction => 'Tietyö';

  @override
  String get categoryHazard => 'Vaara';

  @override
  String get categoryRoadCondition => 'Tien kunto';

  @override
  String get categoryPothole => 'Kuoppa';

  @override
  String get categoryFog => 'Sumu';

  @override
  String get categoryIce => 'Jää';

  @override
  String get categoryAnimal => 'Eläin';

  @override
  String get categoryOther => 'Muu';

  @override
  String get dateTimeLabel => 'Päivämäärä / aika';

  @override
  String minutesAgo(int count) {
    return '$count min sitten';
  }

  @override
  String hoursAgo(int count) {
    return '${count}t sitten';
  }

  @override
  String daysAgo(int count) {
    return '${count}pv sitten';
  }

  @override
  String get sectionFavorites => 'Tallennetut paikat';

  @override
  String get addFavorite => 'Lisää paikka';

  @override
  String get favoriteLabelHint => 'Nimi (esim. Koti, Toimisto)';

  @override
  String get favoriteAddressHint => 'Osoite';

  @override
  String get favoriteGeocodingError =>
      'Osoitetta ei löydy. Kokeile tarkempaa osoitetta.';

  @override
  String get trafficAlertTitle => 'Uutta liikennettä reitillä';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category ilmoitettu $age reitilläsi.\n\nHaluatko löytää vaihtoehtoisen reitin?';
  }

  @override
  String get trafficContinue => 'Jatka';

  @override
  String get trafficRecalculate => 'Laske reitti uudelleen';

  @override
  String get navExitTitle => 'Lopeta navigointi?';

  @override
  String get navExitBody => 'Haluatko lopettaa navigoinnin ja palata karttaan?';

  @override
  String get navContinue => 'Jatka navigointia';

  @override
  String get navExit => 'Kyllä, lopeta';

  @override
  String get loadingInfo => 'Ladataan tietoja…';

  @override
  String get conditionsOnRoute => 'Olosuhteet reitillä';

  @override
  String get calculateRoute => 'Laske reitti';

  @override
  String get sectionNavigationVoice => 'Navigoinnin ääni';

  @override
  String get voiceGuidance => 'Ääniopastus';

  @override
  String get voiceGuidanceDesc => 'Lue käännösohjeet ääneen navigoinnin aikana';

  @override
  String get testVoiceEngine => 'Testaa äänimoottori';

  @override
  String get testVoiceEngineDesc =>
      'Napauta tarkistaaksesi TTS-moottorin ja saadaksesi asennusohjeet';

  @override
  String get ttsDialogTitle => 'Äänimoottori puuttuu';

  @override
  String get ttsDialogBody =>
      'Toimivaa Text-to-Speech-moottoria ei löytynyt.\n\nRoadstr tukeutuu vain avoimen lähdekoodin ohjelmistoihin — asenna jokin näistä ilmaisista moottoreista F-Droidista:';

  @override
  String get ttsRhvoiceDesc =>
      'Luonnollisen kuuloinen ääni, rajoitettu kielilista';

  @override
  String get ttsEspeakDesc => 'Tukee yli 100 kieltä, robottimainen ääni';

  @override
  String get ttsInstallNote =>
      '⚠️ Asennuksen jälkeen:\n1. Android-asetukset → Esteettömyys → Tekstistä puheeksi\n2. Valitse juuri asentamasi moottori\n3. Lataa kielesi äänidata\n4. Käynnistä Roadstr kokonaan uudelleen';

  @override
  String get ttsTestNow => 'Testaa nyt';

  @override
  String get voiceUnsupportedTitle => 'Ääniopastus ei ole käytettävissä';

  @override
  String get voiceUnsupportedBody =>
      'Kieltäsi ei vielä tueta puhutuissa käännösohjeissa. Navigointiohjeet näkyvät edelleen tekstinä näytöllä.';

  @override
  String get kokoroModelTitle => 'Ääntömalli (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Ei ladattu · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Ladataan...';

  @override
  String get kokoroModelStatusReady => 'Ääntömalli valmis';

  @override
  String get kokoroModelDownloadBtn => 'Lataa';

  @override
  String get kokoroModelSupportedLangs =>
      'Tukee: italia, englanti, espanja, ranska, japani, kiina, portugali';

  @override
  String get autoDarkMode => 'Automaattinen tumma teema';

  @override
  String get autoDarkModeDesc =>
      'Ottaa tumman teeman käyttöön auringonlaskun aikaan';

  @override
  String get settingsImperialUnits => 'Imperiaaliset yksiköt';

  @override
  String get settingsImperialUnitsDesc =>
      'Mailit ja jalat kilometrien ja metrien sijaan';

  @override
  String get arrivedTitle => '🎉 Olet perillä!';

  @override
  String get arrivedBody => 'Olet saapunut määränpäähäsi.';

  @override
  String get arrivedFeedbackPrompt => 'Miten meni?';

  @override
  String get feedbackBad => 'Huonosti';

  @override
  String get feedbackGood => 'Hienosti!';

  @override
  String get feedbackDialogTitle => 'Kerro meille, mikä meni pieleen';

  @override
  String get feedbackHint => 'Kuvaile ongelma…';

  @override
  String get feedbackSent => 'Palaute lähetetty — kiitos! 🙏';

  @override
  String get feedbackSubmit => 'Lähetä';

  @override
  String get transportModeCar => 'Auto';

  @override
  String get transportModeWalk => 'Kävellen';

  @override
  String etaArrivalLabel(String time) {
    return 'Perillä $time';
  }

  @override
  String get supportRoadstr => 'Tue Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address kopioitu leikepöydälle';
  }

  @override
  String get disclaimerTitle => 'Tärkeä ilmoitus';

  @override
  String get disclaimerAccept => 'Olen lukenut ja hyväksyn';

  @override
  String get disclaimerBody =>
      'Roadstr on kokeellinen, avoimen lähdekoodin ja yhteisön ylläpitämä navigointisovellus, joka perustuu OpenStreetMap-tietoihin ja Nostr-protokollaan, ja se on käytettävissä missä tahansa maassa. Lataamalla, asentamalla tai käyttämällä tätä sovellusta käyttäjä hyväksyy ehdoitta kaikki seuraavat ehdot ilman alueellista rajoitusta.\n\n🚗 LIIKENNETURVALLISUUS ENNEN KAIKKEA\nKuljettajan on aina pidettävä katse tiellä ja noudatettava kaikkia sovellettavia liikennesääntöjä ja asetettuja liikennemerkkejä, joilla on aina etusija sovelluksen antamiin ohjeisiin nähden. Älä koskaan käytä laitetta ajon aikana; kiinnitä se hyväksyttyyn, näkyvään telineeseen ennen liikkeelle lähtöä, äläkä koskaan käännä huomiotasi pois tieltä laitteen käyttämiseksi ajoneuvon liikkuessa.\n\n⚠️ RISKIN HYVÄKSYMINEN — MAAILMANLAAJUISESTI\nKäyttämällä Roadstr-sovellusta missä tahansa maassa ja minkä tahansa oikeusjärjestelmän alaisuudessa käyttäjä ottaa tietoisesti ja vapaaehtoisesti kantaakseen KAIKKI sen käyttöön liittyvät riskit, mukaan lukien muun muassa: liikenneonnettomuudet, henkilövahingot, kuolema, omaisuusvahingot, ajoneuvovauriot, sakot, hallinnolliset seuraamukset, hinaus, ajoneuvon takavarikointi, rikosoikeudellinen vastuu tai mikä tahansa muu seuraus, joka aiheutuu suoraan tai välillisesti sovellukseen luottamisesta. Käyttäjä yksin kantaa täyden vastuun jokaisesta ajamiseen ja navigointiin liittyvästä päätöksestä.\n\n🚫 EI TAKUUTA\nRoadstr tarjotaan tiukasti sellaisena kuin se on (”AS IS”) ja sellaisena kuin se on saatavilla (”AS AVAILABLE”), ilman minkäänlaista takuuta, olipa se nimenomainen, hiljainen tai lakisääteinen — mukaan lukien, rajoituksetta, takuut tarkkuudesta, täydellisyydestä, luotettavuudesta, saatavuudesta, kaupattavuudesta, tiettyyn tarkoitukseen soveltuvuudesta ja oikeuksien loukkaamattomuudesta. Karttatiedot, reititys, nopeusrajoitukset, nopeusvalvontakamerat ja ajoneuvoliikenteen rajoitusalueita (ZTL/ZAC/LTZ) koskevat tiedot ovat peräisin avoimista, yhteisön ylläpitämistä lähteistä (OpenStreetMap, Overpass API), jotka voivat minkä tahansa maan, alueen tai kunnan osalta olla milloin tahansa ja ilman ennakkoilmoitusta puutteellisia, vanhentuneita tai virheellisiä. Käyttäjä on yksin vastuussa siitä, että hän varmistaa itsenäisesti ennen matkaa ja sen aikana ehdotetun reitin laillisuuden ja käytettävyyden virallisten paikallisten liikennemerkkien ja määräysten perusteella.\n\n📍 TARKKUUS JA GPS\nGPS-paikannus voi olla epätarkka tai käyttökelvoton. Kaikki ohjeet, etäisyydet ja hälytykset annetaan vain suuntaa-antavina, eikä niihin saa koskaan luottaa ajopäätöksen ainoana perusteena.\n\n🛡️ VASTUUN RAJOITTAMINEN\nSovellettavan lain sallimissa laajimmissa rajoissa missä tahansa lainkäyttöalueella kehittäjät, avustajat ja kaikki Roadstr-sovelluksen luomiseen tai jakeluun osallistuneet tahot eivät ole vastuussa mistään välittömistä, välillisistä, satunnaisista, seuraamuksellisista, erityisistä, esimerkinomaisista tai rankaisevista vahingoista, olivatpa ne minkälaisia tahansa — mukaan lukien henkilövahinko, kuolema tai taloudellinen menetys —, jotka aiheutuvat sovelluksen käytöstä tai kyvyttömyydestä käyttää sitä tai liittyvät siihen, vaikka tällaisten vahinkojen mahdollisuudesta olisi ilmoitettu. Jos jokin lainkäyttöalue ei salli tätä rajoitusta osittain tai kokonaan, vastuu rajoitetaan pienimpään kyseisen lainkäyttöalueen lain sallimaan laajuuteen.\n\n🤝 VAHINGONKORVAUSVELVOLLISUUS\nKäyttäjä sitoutuu korvaamaan ja pitämään vahingoittumattomina kehittäjät ja avustajat kaikista vaatimuksista, vahingoista, menetyksistä tai kuluista (mukaan lukien oikeudenkäyntikulut), jotka aiheutuvat käyttäjän sovelluksen käytöstä tai näiden ehtojen rikkomisesta.\n\n🔒 YKSITYISYYS\nSijaintitietoja ei siirretä Roadstrin omille palvelimille. Reitin laskenta suoritetaan kolmansien osapuolten palveluiden (OSRM, GraphHopper, OpenRouteService) kautta, joille lähetetään ainoastaan lähtö- ja määränpääkoordinaatit.\n\n⚖️ EROTETTAVUUS\nJos jokin näiden ehtojen määräys todetaan täytäntöönpanokelvottomaksi tietyllä lainkäyttöalueella, kyseistä määräystä rajoitetaan tai se erotetaan mahdollisimman vähäisessä tarpeellisessa määrin, ja kaikki muut määräykset pysyvät täysin voimassa.\n\nKäyttämällä Roadstr-sovellusta missä tahansa maailmassa käyttäjä vahvistaa lukeneensa, ymmärtäneensä ja hyväksyneensä ehdoitta jokaisen yllä mainitun ehdon sekä ottavansa täyden ja kokonaisen vastuun — ja kaiken riskin — sovelluksen käytöstä ja siitä aiheutuvista seurauksista.';

  @override
  String get readOnWikipedia => 'Lue Wikipediasta';

  @override
  String searchOnEngine(String engine) {
    return 'Hae $engine';
  }

  @override
  String get plannerFromHint => 'Lähtöpiste…';

  @override
  String get plannerToHint => 'Määränpää…';

  @override
  String departEta(String dep, String arr) {
    return 'Lähtö $dep  →  Saapuminen $arr';
  }

  @override
  String get modeCar => 'Auto';

  @override
  String get modeBike => 'Pyörä';

  @override
  String get modeWalk => 'Kävellen';

  @override
  String windSpeed(String speed) {
    return 'tuuli $speed km/h';
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
  String get weatherClear => 'Selkeä';

  @override
  String get weatherPartlyCloudy => 'Puolipilvinen';

  @override
  String get weatherCloudy => 'Pilvinen';

  @override
  String get weatherFog => 'Sumu';

  @override
  String get weatherLightRain => 'Kevyt sade';

  @override
  String get weatherRain => 'Sade';

  @override
  String get weatherSnow => 'Lumi';

  @override
  String get weatherShowers => 'Sadekuurot';

  @override
  String get weatherThunderstorm => 'Ukkosmyrsky';

  @override
  String get ztlAheadWarning =>
      'Edessä on rajoitettu liikennealue — reitti kulkee sen läpi';

  @override
  String get ztlInsideWarning => 'Rajoitettu liikennealue';

  @override
  String get onboardingAppSubtitle => 'Avoimen lähdekoodin Nostr-navigointi';

  @override
  String get onboardingWelcomeTitle => 'Tervetuloa';

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
  String get onboardingGetStarted => 'Aloita';

  @override
  String get onboardingNostrTitle => 'Nostr-identiteetti';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Yhdistetty';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (suositeltu)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec-avain';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Virheellinen nsec-avain — tarkista ja yritä uudelleen.';

  @override
  String get onboardingSkip => 'Ohita nyt';

  @override
  String get onboardingContinue => 'Jatka';

  @override
  String get onboardingEnterNsec => 'Syötä nsec-avain';

  @override
  String get onboardingSetupTitle => 'Määritä Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Sijainti';

  @override
  String get onboardingLocationGranted => 'Sijaintioikeus myönnetty';

  @override
  String get onboardingLocationRequired => 'Tarvitaan GPS-navigointiin';

  @override
  String get onboardingGrantButton => 'Myönnä';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS-käyttäjät';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'AI-ääniohjelma (valinnainen)';

  @override
  String get onboardingVoiceReady => 'Kokoro äänimalli valmis';

  @override
  String get onboardingVoiceDownloading => 'Ladataan äänimaalia…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Lataa';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Olet valmis navigoimaan!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Mennään!';

  @override
  String get onboardingProfileLoading => 'Ladataan profiilia…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Ääni';

  @override
  String get kokoroVoiceFemale => 'Naisääni';

  @override
  String get kokoroVoiceMale => 'Miesääni';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Miesääni ei ole saatavilla tälle kielelle';

  @override
  String get kokoroSpeedTitle => 'Puhenopeus';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Valinnainen: tallennetut suosikkisi voivat synkronoitua laitteidesi välillä Nostr-releiden kautta, päästä päähän salattuna (NIP-44) omalla avaimellasi — releet näkevät vain salattua tekstiä, eikä kukaan muu kuin sinä voi lukea sisältöä. Ota käyttöön milloin tahansa Asetuksista.';

  @override
  String get parkingSaveHere => 'Tallenna pysäköinti tähän';

  @override
  String get parkingMarkerTitle => 'Pysäköintipaikka';

  @override
  String get parkingNavigateHere => 'Navigoi pysäköintiin';

  @override
  String get parkingRemove => 'Poista pysäköinti';

  @override
  String get parkingSavedSnack => 'Pysäköintipaikka tallennettu';

  @override
  String get parkingRemovedSnack => 'Pysäköintipaikka poistettu';

  @override
  String get exportFavoritesTitle => 'Vie suosikit';

  @override
  String get exportFavoritesDesc =>
      'Tallenna suosikkipaikkasi JSON-tiedostoon, jonka voit varmuuskopioida tai siirtää toiselle laitteelle.';

  @override
  String get exportEncryptToggle => 'Salaa salasanalla';

  @override
  String get exportPasswordHint => 'Salasana';

  @override
  String get exportButton => 'Vie';

  @override
  String get exportSuccessSnack => 'Suosikit viety';

  @override
  String get exportFailedSnack => 'Vienti epäonnistui';

  @override
  String get importFavoritesTitle => 'Tuo suosikit';

  @override
  String get importPasswordPrompt => 'Tämä tiedosto on salattu — anna salasana';

  @override
  String importSuccessSnack(int n) {
    return '$n suosikkia tuotu';
  }

  @override
  String get importFailedSnack =>
      'Tuonti epäonnistui — tarkista tiedosto ja salasana';

  @override
  String get syncFavoritesTitle => 'Synkronoi suosikit';

  @override
  String get syncFavoritesDesc =>
      'Julkaise suosikkisi päästä päähän salattuna Nostr-releillesi, jotta ne seuraavat sinua kaikilla laitteillasi. Releet näkevät vain salattua tekstiä — kukaan muu kuin sinä ei voi lukea sisältöä.';

  @override
  String get syncNowButton => 'Lähetä Nostriin';

  @override
  String get syncPullButton => 'Nouda Nostrista';

  @override
  String get syncSuccessSnack => 'Suosikit synkronoitu';

  @override
  String get syncFailedSnack => 'Synkronointi epäonnistui';

  @override
  String syncLastSyncLabel(String when) {
    return 'Viimeksi synkronoitu: $when';
  }

  @override
  String get syncNoIdentity =>
      'Kirjaudu Nostrilla (Asetukset → Profiili) ottaaksesi synkronoinnin käyttöön';

  @override
  String get onboardingVpnNotice =>
      'Maksimaalisen yksityisyyden vuoksi — ilmoitukset leviävät Nostr-verkossa — VPN:n käyttö on suositeltavaa. Mullvad, jonka voi maksaa Bitcoinilla, on suositeltu valinta.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Luotettavan toiminnan varmistamiseksi aseta sovelluksen sijaintilupa tilaan ”Salli aina”, ei vain sovellusta käytettäessä.';

  @override
  String get trafficNormal => 'Normaali liikenne';

  @override
  String get trafficModerate => 'Kohtalainen liikenne';

  @override
  String get trafficHeavy => 'Vilkas liikenne';

  @override
  String get avoidHighwaysChip => 'Vältä moottoriteitä';

  @override
  String get avoidTollsChip => 'Vältä tietulleja';

  @override
  String get preferShorterChip => 'Lyhin reitti';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Näytä reitin esikatselu';
}
