// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Croatian (`hr`).
class AppLocalizationsHr extends AppLocalizations {
  AppLocalizationsHr([String locale = 'hr']) : super(locale);

  @override
  String get searchHint => 'Kamo želite ići?';

  @override
  String get calculatingRoute => 'Izračunavam rutu…';

  @override
  String get routeNotFoundTitle => 'Ruta nije pronađena';

  @override
  String get noRouteFound => 'Nije pronađena nijedna ruta. Provjerite vezu.';

  @override
  String get emptyServerResponse =>
      'Prazan odgovor poslužitelja. Provjerite vezu.';

  @override
  String routeError(String error) {
    return 'Pogreška pri izračunu rute: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS nije dostupan — Postavke → Aplikacija → Roadstr → Dozvole → Lokacija';

  @override
  String get acquiringGps => 'Dohvaćam GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopper poslužitelj nije konfiguriran — koristi se OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper API ključ nije konfiguriran — koristi se OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService API ključ nije konfiguriran — koristi se OSRM';

  @override
  String get chooseRoute => 'Odaberi rutu';

  @override
  String routeOptionsCount(int count) {
    return '$count opcija';
  }

  @override
  String get cancel => 'Odustani';

  @override
  String get startNavigation => 'Pokreni navigaciju';

  @override
  String get fastestRoute => 'Najbrža';

  @override
  String get now => 'Sada';

  @override
  String get history => 'Povijest';

  @override
  String get clearHistory => 'Obriši';

  @override
  String get selectedPosition => 'Odabrana pozicija';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Izbornik';

  @override
  String get settingsTitle => 'Postavke';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Karta';

  @override
  String get sectionPrivacy => 'Privatnost';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Jezik';

  @override
  String get themeLightNostr => 'Svjetla · Nostr Ljubičasta';

  @override
  String get themeLightBitcoin => 'Svjetla · Bitcoin Narančasta';

  @override
  String get langSystem => 'Zadano sustava';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Drži zaslon uključenim';

  @override
  String get keepScreenOnDescription =>
      'Sprječava uspavljivanje za vrijeme navigacije';

  @override
  String get rotateMap => 'Karta prati smjer';

  @override
  String get rotateMapDescription => 'Karta se rotira prema smjeru vožnje';

  @override
  String get mapTileUrlLabel => 'URL pločica karte';

  @override
  String get routingProviderLabel => 'Davatelj usmjeravanja';

  @override
  String get osrmProvider => 'OSRM (javan, nije potreban ključ)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokalni/privatni)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API ključ)';

  @override
  String get openrouteProvider => 'OpenRouteService (API ključ)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API ključ (neobavezno)';

  @override
  String get verify => 'Provjeri';

  @override
  String get graphhopperServerUrlRequired =>
      'Unesite URL poslužitelja prije provjere.';

  @override
  String get successTitle => 'Uspjeh';

  @override
  String get graphhopperServerReachable =>
      'GraphHopper poslužitelj je dostupan';

  @override
  String get errorTitle => 'Pogreška';

  @override
  String get close => 'Zatvori';

  @override
  String get privacyMode => 'Način privatnosti';

  @override
  String get privacyModeDescription =>
      'Ne šalji anonimne telemetrijske podatke';

  @override
  String get infoVersion => 'Verzija';

  @override
  String get infoProtocol => 'Protokol';

  @override
  String get infoMaps => 'Karte';

  @override
  String get infoRouting => 'Usmjeravanje';

  @override
  String get infoSource => 'Izvor';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted =>
      'GraphHopper (samostalno hostiran)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (oblak)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Nije spojeno';

  @override
  String get loginWithNostrTitle => 'PRIJAVA S NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Privatni ključ nikada ne napušta vaš uređaj · Preporučeno';

  @override
  String get nsecLoginOption => 'Unesite nsec';

  @override
  String get nsecLoginDescription =>
      'Privatni ključ pohranjen lokalno · Manje sigurno';

  @override
  String get connectedViaAmber => 'Spojeno putem Ambera (NIP-55)';

  @override
  String get connectedViaNsec => 'Spojeno putem nsec-a';

  @override
  String get publicKeyLabel => 'JAVNI KLJUČ';

  @override
  String get npubCopiedToClipboard => 'npub kopiran u međuspremnik';

  @override
  String get logoutTitle => 'Odspoji';

  @override
  String get logoutConfirmation =>
      'Ukloniti Nostr vjerodajnice s ovog uređaja?';

  @override
  String get logoutButton => 'Odspoji';

  @override
  String get nostrIdentityInfo =>
      'S Nostr identitetom možete objavljivati prometna upozorenja, izvješća i zanimljiva mjesta na decentraliziran način u Nostr mreži, bez središnjih poslužitelja.';

  @override
  String get warningTitle => 'Upozorenje';

  @override
  String get nsecWarning =>
      'Spremate se unijeti svoj Nostr privatni ključ izravno u aplikaciju. Svatko s fizičkim ili udaljenim pristupom vašem uređaju može ga pročitati i trajno preuzeti kontrolu nad vašim Nostr identitetom.';

  @override
  String get amberSecureMethodHint =>
      '✦  Sigurna metoda je Amber (NIP-55): nsec nikada ne napušta sef potpisnika aplikacije.';

  @override
  String get nsecRiskAcknowledgment =>
      'Razumijem rizik i svejedno želim nastaviti';

  @override
  String get continueButton => 'Nastavi';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber je NIP-55 kompatibilni Android potpisnik aplikacija. Vaš privatni ključ ostaje izoliran unutar Ambera i nikada se ne dijeli.';

  @override
  String get requestKeyFromAmber => 'Zatraži javni ključ od Ambera';

  @override
  String get amberNotFound =>
      'Amber nije pronađen. Instalirajte ga iz Play Storea ili ručno unesite svoj npub.';

  @override
  String get waitingForAmberResponse => 'Čekam odgovor od Ambera…';

  @override
  String get pasteNpubManually => 'Zalijepite svoj npub ručno:';

  @override
  String get confirmNpub => 'Potvrdi npub';

  @override
  String get enterNsecTitle => 'Unesite nsec';

  @override
  String get loginButton => 'Prijava';

  @override
  String get invalidNpubTitle => 'Nevažeći npub';

  @override
  String get invalidNsecTitle => 'Nevažeći nsec';

  @override
  String get invalidNpubMessage =>
      'Provjerite jeste li zalijepili ispravan npub.';

  @override
  String get invalidNsecMessage =>
      'Provjerite jeste li zalijepili ispravan nsec.';

  @override
  String get amberResponseError => 'Pogreška odgovora Ambera';

  @override
  String get ok => 'OK';

  @override
  String get or => 'ili';

  @override
  String get gpsNotActiveTitle => 'GPS nije aktivan';

  @override
  String get gpsDisabledMessage =>
      'Aktivirajte GPS u postavkama uređaja kako biste dobili svoju lokaciju i koristili navigaciju.';

  @override
  String get openSettings => 'Postavke';

  @override
  String get myLocation => 'Moja lokacija';

  @override
  String get loginToReport =>
      'Prijavite se s Nostr (odjeljak Profil) za prijavu događaja';

  @override
  String get navigateHere => 'Navigiraj ovdje';

  @override
  String get reportEventHere => 'Prijavi događaj ovdje';

  @override
  String get zapSendSats => 'Zap ⚡ (pošalji sats)';

  @override
  String get sendZap => 'Pošalji Zap';

  @override
  String get chooseAmountSats => 'Odaberite iznos u satoshijima (sats):';

  @override
  String get customAmount => 'Prilagođeni iznos…';

  @override
  String get zapSending => 'Slanje…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Dohvaćam Lightning adresu…';

  @override
  String get noLightningAddress => 'Ovaj izvjestitelj nema Lightning adresu';

  @override
  String get requestingInvoice => 'Tražim račun…';

  @override
  String get lnurlUnavailable => 'LNURL nije dostupan';

  @override
  String get invoiceFailed => 'Nije moguće generirati račun';

  @override
  String get openingWallet => 'Otvaranje novčanika…';

  @override
  String get payingViaNwc => 'Plaćanje putem NWC-a…';

  @override
  String get noLightningWallet => 'Nije pronađen Lightning novčanik';

  @override
  String zapSent(int sats) {
    return '⚡ $sats satsa poslano!';
  }

  @override
  String get stillThere => 'Još uvijek tamo';

  @override
  String get notThereAnymore => 'Više nije tamo';

  @override
  String get loginToConfirm => 'Prijavite se s Nostr za potvrdu ili osporu';

  @override
  String get reportAnEvent => 'Prijavi događaj';

  @override
  String get optionalComment => 'Neobavezan komentar…';

  @override
  String get publish => 'Objavi';

  @override
  String get publishing => 'Objavljivanje…';

  @override
  String get reportPublished => 'Izvješće objavljeno ✓';

  @override
  String get myReports => 'MOJA IZVJEŠĆA';

  @override
  String get noReportsYet => 'Nema objavljenih izvješća';

  @override
  String get zapBalance => 'Zap saldo';

  @override
  String get satoshiFromReports => 'Satoshiji primljeni iz vaših izvješća';

  @override
  String get reputationHigh => 'Visoka';

  @override
  String get reputationMedium => 'Srednja';

  @override
  String get reputationLow => 'Niska';

  @override
  String reputationLabel(String level) {
    return 'Ugled $level';
  }

  @override
  String reliability(int pct) {
    return 'Pouzdanost: $pct%';
  }

  @override
  String get confirmedLabel => 'Potvrđeno';

  @override
  String get removedLabel => 'Uklonjeno';

  @override
  String get positionLabel => 'Pozicija';

  @override
  String get loadingLabel => 'Učitavanje…';

  @override
  String get sectionWebSearch => 'Web pretraživanje';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Zalijepite svoj NWC URI (Alby Hub, Mutiny, Cashu…) za plaćanje Zapova izravno iz aplikacije.';

  @override
  String get searchEngineQwantDesc =>
      'Europski, privatnost na prvom mjestu. Bez praćenja, bez reklamnih profila. Preporučeno.';

  @override
  String get searchEngineBraveDesc =>
      'Nezavisan indeks, otvoreni kod. Bez ovisnosti o Googleu ili Bingu. Nula profiliranja.';

  @override
  String get searchEngineDdgDesc =>
      'Usmjeren na privatnost i popularan. Rezultati djelomično iz Binga — imajte to na umu.';

  @override
  String get searchEngineStartpageDesc =>
      'Google rezultati bez predavanja vaših podataka Googleu. Razuman kompromis.';

  @override
  String get searchEngineGoogleDesc =>
      'Vrlo učinkovit. Ali zapamtite: Google vas poznaje bolje od vaše majke i prodaje sve oglašivačima. Vaš izbor. 🍪';

  @override
  String get categoryPolice => 'Policija';

  @override
  String get categorySpeedCamera => 'Kamera za brzinu';

  @override
  String get categoryTrafficJam => 'Prometna gužva';

  @override
  String get categoryAccident => 'Nesreća';

  @override
  String get categoryRoadClosure => 'Zatvorena cesta';

  @override
  String get categoryConstruction => 'Radovi';

  @override
  String get categoryHazard => 'Opasnost';

  @override
  String get categoryRoadCondition => 'Stanje ceste';

  @override
  String get categoryPothole => 'Rupa na cesti';

  @override
  String get categoryFog => 'Magla';

  @override
  String get categoryIce => 'Led';

  @override
  String get categoryAnimal => 'Životinja';

  @override
  String get categoryOther => 'Ostalo';

  @override
  String get dateTimeLabel => 'Datum / vrijeme';

  @override
  String minutesAgo(int count) {
    return 'prije $count min';
  }

  @override
  String hoursAgo(int count) {
    return 'prije ${count}h';
  }

  @override
  String daysAgo(int count) {
    return 'prije ${count}d';
  }
}
