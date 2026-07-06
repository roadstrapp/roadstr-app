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
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Jezik';

  @override
  String get themeLightNostr => 'Svjetla · Nostr Ljubičasta';

  @override
  String get themeLightBitcoin => 'Svjetla · Bitcoin Narančasta';

  @override
  String get themeDarkNostr => 'Tamna · Nostr Ljubičasta';

  @override
  String get themeDarkBitcoin => 'Tamna · Bitcoin Narančasta';

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

  @override
  String get sectionFavorites => 'Spremljene lokacije';

  @override
  String get addFavorite => 'Dodaj lokaciju';

  @override
  String get favoriteLabelHint => 'Naziv (npr. Dom, Ured)';

  @override
  String get favoriteAddressHint => 'Adresa';

  @override
  String get favoriteGeocodingError =>
      'Adresa nije pronađena. Pokušaj s preciznijom adresom.';

  @override
  String get trafficAlertTitle => 'Novi promet na ruti';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category prijavljeno $age na vašoj ruti.\n\nŽelite li pronaći alternativnu rutu?';
  }

  @override
  String get trafficContinue => 'Nastavi';

  @override
  String get trafficRecalculate => 'Preračunaj rutu';

  @override
  String get navExitTitle => 'Izaći iz navigacije?';

  @override
  String get navExitBody =>
      'Želite li zaustaviti navigaciju i vratiti se na kartu?';

  @override
  String get navContinue => 'Nastavi navigaciju';

  @override
  String get navExit => 'Da, izađi';

  @override
  String get loadingInfo => 'Učitavanje informacija…';

  @override
  String get conditionsOnRoute => 'Uvjeti na ruti';

  @override
  String get calculateRoute => 'Izračunaj rutu';

  @override
  String get sectionNavigationVoice => 'Glas navigacije';

  @override
  String get voiceGuidance => 'Glasovno vođenje';

  @override
  String get voiceGuidanceDesc =>
      'Naglas čitaj upute za skretanje tijekom navigacije';

  @override
  String get testVoiceEngine => 'Testiraj glasovni mehanizam';

  @override
  String get testVoiceEngineDesc =>
      'Dodirnite za provjeru TTS mehanizma i upute za postavljanje';

  @override
  String get ttsDialogTitle => 'Nedostaje glasovni mehanizam';

  @override
  String get ttsDialogBody =>
      'Nije pronađen funkcionalan Text-to-Speech mehanizam.\n\nRoadstr se oslanja isključivo na softver otvorenog koda — instalirajte jedan od ovih besplatnih mehanizama s F-Droida:';

  @override
  String get ttsRhvoiceDesc => 'Prirodno zvučeći glas, ograničen popis jezika';

  @override
  String get ttsEspeakDesc => 'Pokriva preko 100 jezika, robotski glas';

  @override
  String get ttsInstallNote =>
      '⚠️ Nakon instalacije:\n1. Android postavke → Pristupačnost → Pretvaranje teksta u govor\n2. Odaberite upravo instalirani mehanizam\n3. Preuzmite glasovne podatke za vaš jezik\n4. Potpuno ponovno pokrenite Roadstr';

  @override
  String get ttsTestNow => 'Testiraj sada';

  @override
  String get voiceUnsupportedTitle => 'Glasovno vođenje nije dostupno';

  @override
  String get voiceUnsupportedBody =>
      'Vaš jezik još nije podržan za glasovne upute za skretanje. Upute za navigaciju i dalje će se prikazivati kao tekst na zaslonu.';

  @override
  String get kokoroModelTitle => 'Glasovni model (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Nije preuzeto · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Preuzimanje...';

  @override
  String get kokoroModelStatusReady => 'Glasovni model spreman';

  @override
  String get kokoroModelDownloadBtn => 'Preuzmi';

  @override
  String get kokoroModelSupportedLangs =>
      'Podržava: talijanski, engleski, španjolski, francuski, japanski, kineski, portugalski';

  @override
  String get autoDarkMode => 'Automatska tamna tema';

  @override
  String get autoDarkModeDesc => 'Aktivira tamnu temu pri zalasku sunca';

  @override
  String get arrivedTitle => '🎉 Stigli ste!';

  @override
  String get arrivedBody => 'Stigli ste na odredište.';

  @override
  String get arrivedFeedbackPrompt => 'Kako je prošlo?';

  @override
  String get feedbackBad => 'Loše';

  @override
  String get feedbackGood => 'Dobro!';

  @override
  String get feedbackDialogTitle => 'Recite nam što je pošlo po krivu';

  @override
  String get feedbackHint => 'Opišite problem…';

  @override
  String get feedbackSent => 'Povratna informacija poslana — hvala! 🙏';

  @override
  String get feedbackSubmit => 'Pošalji';

  @override
  String get transportModeCar => 'Auto';

  @override
  String get transportModeWalk => 'Pješice';

  @override
  String etaArrivalLabel(String time) {
    return 'Dol. $time';
  }

  @override
  String get supportRoadstr => 'Podržite Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address kopirano u međuspremnik';
  }

  @override
  String get disclaimerTitle => 'Važna obavijest';

  @override
  String get disclaimerAccept => 'Pročitao/la sam i prihvaćam';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Pročitaj na Wikipediji';

  @override
  String searchOnEngine(String engine) {
    return 'Traži na $engine';
  }

  @override
  String get plannerFromHint => 'Od…';

  @override
  String get plannerToHint => 'Odredište…';

  @override
  String departEta(String dep, String arr) {
    return 'Polazak $dep  →  Dolazak $arr';
  }

  @override
  String get modeCar => 'Auto';

  @override
  String get modeBike => 'Bicikl';

  @override
  String get modeWalk => 'Pješice';

  @override
  String windSpeed(String speed) {
    return 'vjetar $speed km/h';
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
  String get weatherClear => 'Vedro';

  @override
  String get weatherPartlyCloudy => 'Dijelom oblačno';

  @override
  String get weatherCloudy => 'Oblačno';

  @override
  String get weatherFog => 'Magla';

  @override
  String get weatherLightRain => 'Slaba kiša';

  @override
  String get weatherRain => 'Kiša';

  @override
  String get weatherSnow => 'Snijeg';

  @override
  String get weatherShowers => 'Pljuskovi';

  @override
  String get weatherThunderstorm => 'Oluja';

  @override
  String get ztlAheadWarning =>
      'ZTL zona naprijed — ruta ulazi u ograničenu zonu';

  @override
  String get ztlInsideWarning => 'Ograničena prometna zona';

  @override
  String get onboardingAppSubtitle => 'Nostr navigacija otvorenog koda';

  @override
  String get onboardingWelcomeTitle => 'Dobrodošli';

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
  String get onboardingGetStarted => 'Početak';

  @override
  String get onboardingNostrTitle => 'Nostr identitet';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Spojeno';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (preporučeno)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec ključ';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Nevažeći nsec ključ — provjerite i pokušajte ponovo.';

  @override
  String get onboardingSkip => 'Preskoči za sada';

  @override
  String get onboardingContinue => 'Nastavi';

  @override
  String get onboardingEnterNsec => 'Unesite nsec ključ';

  @override
  String get onboardingSetupTitle => 'Postavi Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Lokacija';

  @override
  String get onboardingLocationGranted => 'Pristup lokaciji odobren';

  @override
  String get onboardingLocationRequired => 'Potrebno za GPS navigaciju';

  @override
  String get onboardingGrantButton => 'Dopusti';

  @override
  String get onboardingGrapheneTitle => 'Korisnici GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'AI glasovno vođenje (opcionalno)';

  @override
  String get onboardingVoiceReady => 'Kokoro glasovni model spreman';

  @override
  String get onboardingVoiceDownloading => 'Preuzimanje glasovnog modela…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Preuzmi';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Spreman si za navigaciju!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Idemo!';

  @override
  String get onboardingProfileLoading => 'Učitavanje profila…';

  @override
  String get onboardingNsecHint => 'nsec1…';
}
