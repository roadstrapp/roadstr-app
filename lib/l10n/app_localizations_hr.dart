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
  String get bottomBarNotifications => 'Obavijesti';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Izbornik';

  @override
  String get notificationsTitle => 'Obavijesti';

  @override
  String get notificationsEmpty => 'Još nema obavijesti';

  @override
  String get notificationsEmptyBody =>
      'Zapovi i reakcije na vaše prometne prijave pojavit će se ovdje.';

  @override
  String get notificationsLoginRequired => 'Povežite svoj Nostr profil';

  @override
  String get notificationsLoginRequiredBody =>
      'Prijavite se pomoću Ambera ili nsec-a da biste primali obavijesti drugih korisnika.';

  @override
  String get settingsTitle => 'Postavke';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Karta';

  @override
  String get sectionNavigationAppearance => 'Navigation appearance';

  @override
  String get speedometerStyleLabel => 'Speedometer';

  @override
  String get speedometerClassic => 'Classic (default)';

  @override
  String get speedometerDigital => 'Digital';

  @override
  String get speedometerAnalog => 'Analog';

  @override
  String get speedometerSport => 'Sport';

  @override
  String get speedometerMinimal => 'Minimal';

  @override
  String get movementCursorStyleLabel => 'Movement cursor';

  @override
  String get cursorVehicleLabel => 'Vozilo';

  @override
  String get cursorColorLabel => 'Boja';

  @override
  String get cursorStandard => 'Standard (default)';

  @override
  String get cursorFormula1 => 'Formula 1';

  @override
  String get cursorSuv => 'SUV';

  @override
  String get cursorRacing => 'Racing car';

  @override
  String get cursorElectric => 'Electric car';

  @override
  String get cursorCity => 'City car';

  @override
  String get cursorClassic500 => 'Klasični Fiat 500';

  @override
  String get cursorColorViolet => 'Ljubičasta';

  @override
  String get cursorColorIndigo => 'Indigo';

  @override
  String get cursorColorBlue => 'Plava';

  @override
  String get cursorColorGreen => 'Zelena';

  @override
  String get cursorColorYellow => 'Žuta';

  @override
  String get cursorColorOrange => 'Narančasta';

  @override
  String get cursorColorRed => 'Crvena';

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
  String get keepScreenOn => 'Drži zaslon uključenim';

  @override
  String get keepScreenOnDescription =>
      'Sprječava uspavljivanje za vrijeme navigacije';

  @override
  String get keepScreenOnAlways => 'Keep screen on outside navigation too';

  @override
  String get keepScreenOnAlwaysDescription =>
      'Also applies while browsing the map without an active route';

  @override
  String get minBrightness => 'Minimum screen brightness';

  @override
  String get minBrightnessDescription =>
      'Stops the screen dimming below this level while the app is open — useful on OLED screens that dim further than the map stays readable at';

  @override
  String get minBrightnessOff => 'Off';

  @override
  String get showAltitude => 'Prikaži nadmorsku visinu';

  @override
  String get showAltitudeDescription =>
      'Mali prikaz nadmorske visine u stvarnom vremenu pored kontrola karte';

  @override
  String get autoCenterOnLaunch => 'Centriraj na moju lokaciju pri pokretanju';

  @override
  String get autoCenterOnLaunchDesc =>
      'Automatski koristi GPS samo ako je dopuštenje za lokaciju već odobreno';

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
  String get profileVisibilityTitle => 'Vidljivost profila';

  @override
  String get profileVisibilityDesc =>
      'Odaberite vide li drugi korisnici Roadstra vaše Nostr ime, npub i sliku.';

  @override
  String get profileVisibilityClear => 'Prikaži profil javno';

  @override
  String get profileVisibilityPseudonymous => 'Pseudonimni način (preporučeno)';

  @override
  String get profileVisibilityPublishError =>
      'Odabir je spremljen lokalno, ali još ga nije bilo moguće objaviti na Nostru.';

  @override
  String get nostrichLabel => 'nostrich';

  @override
  String get profileHiddenNotice => 'Ovaj korisnik koristi pseudonimni način.';

  @override
  String get editSpeedLimit => 'Dodaj ili ažuriraj ograničenje brzine';

  @override
  String get requestSpeedLimit => 'Predloži ograničenje brzine';

  @override
  String get acceptEditRequest => 'Prihvati prijedlog';

  @override
  String get pendingEditRequests => 'Prijedlozi izmjena na čekanju';

  @override
  String get speedLimitHint => 'Ograničenje brzine';

  @override
  String get speedLimitSaved => 'Ograničenje brzine objavljeno';

  @override
  String get editRequestSent => 'Prijedlog poslan autoru prijave';

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
  String get waitingForAmberResponse => 'Čekam odgovor od Ambera…';

  @override
  String get enterNsecTitle => 'Unesite nsec';

  @override
  String get loginButton => 'Prijava';

  @override
  String get invalidNpubTitle => 'Nevažeći npub';

  @override
  String get invalidNsecTitle => 'Nevažeći nsec';

  @override
  String get invalidNsecMessage =>
      'Provjerite jeste li zalijepili ispravan nsec.';

  @override
  String get ok => 'OK';

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
  String get notifZapTitle => 'Primljen zap';

  @override
  String notifZapBody(int sat) {
    return 'Primili ste zap od $sat sats!';
  }

  @override
  String get notifConfirmedTitle => 'Prijava potvrđena';

  @override
  String notifConfirmedBody(String category) {
    return 'Vašu prijavu $category potvrdio je drugi vozač';
  }

  @override
  String get notifDeniedTitle => 'Prijava osporena';

  @override
  String notifDeniedBody(String category) {
    return 'Netko je prijavio da vašeg $category više nema';
  }

  @override
  String chainedManeuver(String first, String second) {
    return '$first, zatim $second';
  }

  @override
  String thenManeuver(String instruction) {
    return 'zatim $instruction';
  }

  @override
  String get reportSpeedLimitHint => 'Ograničenje brzine (neobavezno)';

  @override
  String get reportedSpeedLimit => 'Prijavljeno ograničenje brzine';

  @override
  String speedCameraVoiceAlert(int limit, String unit) {
    return 'Prijavljena kamera, ograničenje $limit $unit';
  }

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
  String get settingsImperialUnits => 'Imperijalne jedinice';

  @override
  String get settingsImperialUnitsDesc =>
      'Milje i stope umjesto kilometara i metara';

  @override
  String get arrivedTitle => '🎉 Stigli ste!';

  @override
  String get arrivalAhead => 'Stići ćete na odredište';

  @override
  String get arrivalAheadLeft => 'Stići ćete na odredište s lijeve strane';

  @override
  String get arrivalAheadRight => 'Stići ćete na odredište s desne strane';

  @override
  String continueForDistance(String distance) {
    return 'nastavite ravno $distance';
  }

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
      'Roadstr je eksperimentalna, open-source navigacijska aplikacija koju održava zajednica, temeljena na podacima OpenStreetMapa i protokolu Nostr, dostupna za korištenje u bilo kojoj državi. Preuzimanjem, instaliranjem ili korištenjem ove aplikacije korisnik bezuvjetno prihvaća sve sljedeće uvjete, bez teritorijalnog ograničenja.\n\n🚗 SIGURNOST U PROMETU NA PRVOM MJESTU\nVozač mora uvijek držati pogled na cesti i pridržavati se svih primjenjivih prometnih propisa i postavljene signalizacije, koji uvijek imaju prednost pred bilo kojom uputom aplikacije. Nikada ne rukujte uređajem tijekom vožnje; prije polaska pričvrstite ga u odobreni, vidljivi držač i nikada ne skrećite pozornost s ceste kako biste s njim komunicirali dok se vozilo kreće.\n\n⚠️ PREUZIMANJE RIZIKA — DILJEM SVIJETA\nKorištenjem aplikacije Roadstr, u bilo kojoj državi i pod bilo kojim pravnim sustavom, korisnik svjesno i dobrovoljno preuzima SVE rizike povezane s njezinim korištenjem, uključujući, ali ne ograničavajući se na: prometne nesreće, tjelesne ozljede, smrt, materijalnu štetu, oštećenje vozila, novčane kazne, upravne sankcije, deblokiranje vozila, oduzimanje vozila, kaznenu odgovornost ili bilo koju drugu posljedicu koja izravno ili neizravno proizlazi iz oslanjanja na aplikaciju. Isključivo korisnik snosi punu odgovornost za svaku odluku o vožnji i navigaciji.\n\n🚫 BEZ JAMSTVA\nRoadstr se pruža strogo „KAKAV JEST” i „KAKAV JE DOSTUPAN”, bez ikakvog jamstva bilo koje vrste, izričitog, podrazumijevanog ili zakonskog — uključujući, bez ograničenja, jamstva točnosti, potpunosti, pouzdanosti, dostupnosti, prikladnosti za promet i podobnosti za određenu svrhu te nepovredivosti prava. Podaci o kartama, izračun rute, ograničenja brzine, kamere za mjerenje brzine i informacije o zonama ograničenog prometa (ZTL/ZAC/LTZ) potječu iz otvorenih izvora koje održava zajednica (OpenStreetMap, Overpass API), koji mogu biti nepotpuni, zastarjeli ili netočni za bilo koju državu, regiju ili općinu, u bilo kojem trenutku i bez prethodne najave. Korisnik je isključivo odgovoran za samostalnu provjeru, prije i tijekom putovanja, zakonitosti i prohodnosti bilo koje predložene rute u odnosu na službenu lokalnu signalizaciju i propise.\n\n📍 TOČNOST I GPS\nGPS pozicioniranje može biti netočno ili nedostupno. Sve upute, udaljenosti i upozorenja pružaju se isključivo kao smjernica i nikada se ne smiju smatrati jedinim temeljem za odluku o vožnji.\n\n🛡️ OGRANIČENJE ODGOVORNOSTI\nU najvećoj mjeri dopuštenoj primjenjivim pravom u bilo kojoj jurisdikciji, razvojni programeri, suradnici i bilo koja strana uključena u stvaranje ili distribuciju aplikacije Roadstr neće biti odgovorni za bilo kakvu izravnu, neizravnu, slučajnu, posljedičnu, posebnu, exemplarnu ili kaznenu štetu bilo koje vrste — uključujući tjelesnu ozljedu, smrt ili financijski gubitak — koja proizlazi iz korištenja ili nemogućnosti korištenja aplikacije ili je s njim povezana, čak i ako su bili upozoreni na mogućnost takve štete. Ako neka jurisdikcija ne dopušta dio ili cijelo ovo ograničenje, odgovornost se ograničava na najmanju mjeru dopuštenu zakonom te jurisdikcije.\n\n🤝 OBEŠTEĆENJE\nKorisnik pristaje obeštetiti i osloboditi od odgovornosti razvojne programere i suradnike za bilo kakav zahtjev, štetu, gubitak ili trošak (uključujući odvjetničke troškove) koji proizlazi iz korištenja aplikacije od strane korisnika ili kršenja ovih uvjeta.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ DJELJIVOST ODREDBI\nAko se utvrdi da je neka odredba ovih uvjeta neprovediva u određenoj jurisdikciji, ta će se odredba ograničiti ili izdvojiti u najmanjoj potrebnoj mjeri, dok sve preostale odredbe ostaju na snazi u punom opsegu.\n\nKorištenjem aplikacije Roadstr, bilo gdje u svijetu, korisnik potvrđuje da je pročitao, razumio i bezuvjetno prihvatio svaki od gore navedenih uvjeta te preuzima punu i potpunu odgovornost — i sav rizik — za korištenje aplikacije i bilo koju posljedicu koja iz toga proizlazi.';

  @override
  String get readOnWikipedia => 'Pročitaj na Wikipediji';

  @override
  String get openInBrowser => 'Otvori u pregledniku';

  @override
  String get wikipediaLoadFailed => 'Wikipedia se nije mogla sigurno učitati.';

  @override
  String get retry => 'Pokušaj ponovno';

  @override
  String get poiDetailsFromOsm => 'Informacije s OpenStreetMapa';

  @override
  String get poiCategory => 'Kategorija';

  @override
  String get poiOperator => 'Upravitelj';

  @override
  String get poiCuisine => 'Kuhinja';

  @override
  String get poiWheelchairYes => 'Pristupačno za invalidska kolica';

  @override
  String get poiWheelchairLimited => 'Ograničen pristup za invalidska kolica';

  @override
  String get poiWheelchairNo => 'Nije pristupačno za invalidska kolica';

  @override
  String get poiContact => 'Kontakt';

  @override
  String get poiAddress => 'Adresa';

  @override
  String get poiWebsite => 'Web-stranica';

  @override
  String get poiAccessPrivate => 'Privatni pristup';

  @override
  String get poiAccessCustomers => 'Samo za korisnike';

  @override
  String get poiAccessPermit => 'Potrebna dozvola';

  @override
  String get poiAccessNo => 'Nema javnog pristupa';

  @override
  String get poiAccessDestination => 'Pristup samo do odredišta';

  @override
  String get poiLightningAccepted => 'Prihvaća Lightning';

  @override
  String get poiBitcoinAccepted => 'Prihvaća Bitcoin';

  @override
  String get poiParkingDetails => 'Parkiralište';

  @override
  String get poiParkingSurface => 'Površinsko';

  @override
  String get poiParkingUnderground => 'Podzemno';

  @override
  String get poiParkingMultiStorey => 'Višekatno';

  @override
  String get poiParkingStreetSide => 'Uz ulicu';

  @override
  String get poiParkingLane => 'Na ulici';

  @override
  String get poiParkingRooftop => 'Na krovu';

  @override
  String get poiFee => 'Naknada';

  @override
  String get poiFree => 'Besplatno';

  @override
  String get poiPaid => 'Uz naplatu';

  @override
  String get poiCapacity => 'Kapacitet';

  @override
  String get poiMaxStay => 'Najdulje zadržavanje';

  @override
  String get poiPrice => 'Cijena';

  @override
  String get poiChargingDetails => 'Punjenje';

  @override
  String get poiConnectorType2 => 'Tip 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Dizel';

  @override
  String get poiPetrol95 => 'Benzin 95';

  @override
  String get poiSmokingAllowed => 'Pušenje dopušteno';

  @override
  String get poiSmokingOutside => 'Pušenje vani';

  @override
  String get poiSmokingAreas => 'Prostori za pušače';

  @override
  String get poiSmokeFree => 'Bez pušenja';

  @override
  String get poiOutdoorSeating => 'Sjedenje na otvorenom';

  @override
  String get poiTakeaway => 'Za van';

  @override
  String get poiTakeawayOnly => 'Samo za van';

  @override
  String get gpsSignalLost => 'Izgubljen GPS signal';

  @override
  String get poiOpenNow => 'Otvoreno';

  @override
  String get poiClosedNow => 'Zatvoreno';

  @override
  String poiOpensAt(String when) {
    return 'Otvara: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Zatvara: $when';
  }

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
    return 'vjetar $speed';
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
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

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
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Glas';

  @override
  String get kokoroVoiceFemale => 'Ženski';

  @override
  String get kokoroVoiceMale => 'Muški';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Muški glas nije dostupan za ovaj jezik';

  @override
  String get kokoroSpeedTitle => 'Brzina govora';

  @override
  String get kokoroVolumeTitle => 'Glasnoća glasa';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Neobavezno: spremljeni favoriti mogu se sinkronizirati između vaših uređaja putem Nostr releja, end-to-end šifrirani (NIP-44) vašim vlastitim ključem — releji vide samo šifrirani tekst i nitko osim vas ne može čitati sadržaj. Omogućite bilo kada u Postavkama.';

  @override
  String get profileVisibilityOnboarding =>
      'Roadstr uvijek objavljuje javni Nostr ključ potreban za provjeru prijave. U pseudonimnom načinu drugi korisnici Roadstra vide samo generički avatar i „nostrich”; u javnom načinu vide i vaše Nostr ime, npub i profilnu sliku. Vaše prijave, lokacije, ugled i primljeni zapovi ostaju javni na Nostru.';

  @override
  String get parkingSaveHere => 'Spremi parkiranje ovdje';

  @override
  String get parkingMarkerTitle => 'Parkirno mjesto';

  @override
  String get parkingNavigateHere => 'Navigiraj do parkiranja';

  @override
  String get parkingRemove => 'Ukloni parkiranje';

  @override
  String get parkingSavedSnack => 'Parkirno mjesto spremljeno';

  @override
  String get parkingRemovedSnack => 'Parkirno mjesto uklonjeno';

  @override
  String get exportFavoritesTitle => 'Izvezi favorite';

  @override
  String get exportFavoritesDesc =>
      'Spremite svoja omiljena mjesta u JSON datoteku koju možete sigurnosno kopirati ili prenijeti na drugi uređaj.';

  @override
  String get exportEncryptToggle => 'Šifriraj lozinkom';

  @override
  String get exportPasswordHint => 'Lozinka';

  @override
  String get exportButton => 'Izvezi';

  @override
  String get exportSuccessSnack => 'Favoriti izvezeni';

  @override
  String get exportFailedSnack => 'Izvoz nije uspio';

  @override
  String get importFavoritesTitle => 'Uvezi favorite';

  @override
  String get importPasswordPrompt =>
      'Ova datoteka je šifrirana — unesite lozinku';

  @override
  String importSuccessSnack(int n) {
    return 'Uvezeno $n favorita';
  }

  @override
  String get importFailedSnack =>
      'Uvoz nije uspio — provjerite datoteku i lozinku';

  @override
  String get syncFavoritesTitle => 'Sinkroniziraj favorite';

  @override
  String get syncFavoritesDesc =>
      'Objavite svoje favorite, end-to-end šifrirane, na svoje Nostr releje kako bi vas pratili na svim uređajima. Releji vide samo šifrirani tekst — nitko osim vas ne može čitati sadržaj.';

  @override
  String get syncNowButton => 'Pošalji na Nostr';

  @override
  String get syncPullButton => 'Dohvati s Nostra';

  @override
  String get syncPassphraseTitle =>
      'Sigurnosna fraza sinkronizacije (neobavezno)';

  @override
  String get syncPassphraseDesc =>
      'Drugi sloj šifriranja za sinkronizirane favorite: čak i ako bi vaš Nostr ključ bio ugrožen, snimka na relejima ostaje nečitljiva bez ove fraze. Unosite je jednom na svakom novom uređaju. Ostavite prazno za isključivanje.';

  @override
  String get syncCustomRelayTitle => 'Vlastiti relej (neobavezno)';

  @override
  String get syncCustomRelayDesc =>
      'Dodajte relej koji sami održavate ili plaćate i favoriti će se objavljivati i ondje. Javni releji ne obećavaju ništa o tome koliko dugo čuvaju podatke; vaš obećava. Ostavite prazno za korištenje samo ugrađenih releja.';

  @override
  String get syncCustomRelayInvalid =>
      'Nevaljana adresa releja. Mora počinjati s wss://';

  @override
  String get syncSuccessSnack => 'Favoriti sinkronizirani';

  @override
  String get syncFailedSnack => 'Sinkronizacija nije uspjela';

  @override
  String syncLastSyncLabel(String when) {
    return 'Zadnja sinkronizacija: $when';
  }

  @override
  String get syncNoIdentity =>
      'Prijavite se s Nostrom (Postavke → Profil) za omogućavanje sinkronizacije';

  @override
  String get onboardingVpnNotice =>
      'Za maksimalnu privatnost — prijave se šire Nostr mrežom — preporučuje se korištenje VPN-a. Mullvad, uz plaćanje Bitcoinom, preporučeni je izbor.';

  @override
  String get trafficNormal => 'Normalan promet';

  @override
  String get trafficModerate => 'Umjeren promet';

  @override
  String get trafficHeavy => 'Gust promet';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get avoidHighwaysAndTolls => 'Izbjegavaj autoceste i cestarine';

  @override
  String get avoidRouteUnavailable =>
      'Nije pronađena ruta koja izbjegava i autoceste i ceste s naplatom.';

  @override
  String get avoidanceUnavoidableSection =>
      'Smanjuje autoceste/cestarine · neizbježna dionica';

  @override
  String get nearbyTitle => 'U blizini';

  @override
  String get nearbyNeedsGps => 'Čekanje GPS-a';

  @override
  String get nearbyNothingFound => 'Ništa nije pronađeno unutar 5 km';

  @override
  String get nearbyFuel => 'Benzinska';

  @override
  String get nearbySupermarket => 'Supermarket';

  @override
  String get nearbyAtm => 'Bankomat';

  @override
  String get nearbyPharmacy => 'Ljekarna';

  @override
  String get nearbyHospital => 'Bolnica';

  @override
  String get nearbyPolice => 'Policija';

  @override
  String get nearbyPostOffice => 'Pošta';

  @override
  String get nearbyParking => 'Parkiralište';

  @override
  String get nearbyCharging => 'Punjenje';

  @override
  String get transportModeTransit => 'Prijevoz';

  @override
  String get transitNoServiceHere =>
      'Nema podataka o javnom prijevozu za ovo područje';

  @override
  String get transitRequestFailed => 'Javni prijevoz nije moguće učitati';

  @override
  String get transitRetry => 'Pokušaj ponovno';

  @override
  String get transitWalkLeg => 'Pješice';

  @override
  String get transitYourLocation => 'Vaša lokacija';

  @override
  String get transitDestination => 'Odredište';

  @override
  String get transitScheduledTimes =>
      'Vremena potječu iz objavljenih voznih redova';

  @override
  String get transitDirect => 'Izravno';

  @override
  String transitTransfers(int count) {
    return '$count presjedanja';
  }

  @override
  String ztlNearRouteWarning(String street) {
    return 'Pozor: $street je zona ograničenog prometa';
  }

  @override
  String get themeModernNostr => 'Moderna · Nostr ljubičasta';

  @override
  String get themeModernBitcoin => 'Moderna · Bitcoin narančasta';

  @override
  String get themeModernDarkNostr => 'Moderna tamna · Nostr ljubičasta';

  @override
  String get themeModernDarkBitcoin => 'Moderna tamna · Bitcoin narančasta';

  @override
  String get plannerAddStop => 'Dodaj postaju';

  @override
  String get plannerStopHint => 'Usputna postaja';

  @override
  String get plannerStopsFull => 'Više postaja nije moguće dodati';

  @override
  String plannerStopAdded(String place) {
    return 'Dodana postaja: $place';
  }
}
