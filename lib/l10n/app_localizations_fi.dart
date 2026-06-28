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
  String get sectionPrivacy => 'Yksityisyys';

  @override
  String get sectionInfo => 'Tiedot';

  @override
  String get sectionLanguage => 'Kieli';

  @override
  String get themeLightNostr => 'Vaalea · Nostr Violetti';

  @override
  String get themeLightBitcoin => 'Vaalea · Bitcoin Oranssi';

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
  String get privacyMode => 'Yksityisyystila';

  @override
  String get privacyModeDescription => 'Älä lähetä anonyymiä telemetriadataa';

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
}
