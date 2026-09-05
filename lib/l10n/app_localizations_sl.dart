// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Slovenian (`sl`).
class AppLocalizationsSl extends AppLocalizations {
  AppLocalizationsSl([String locale = 'sl']) : super(locale);

  @override
  String get searchHint => 'Kam želite iti?';

  @override
  String get calculatingRoute => 'Izračunavanje poti…';

  @override
  String get routeNotFoundTitle => 'Pot ni bila najdena';

  @override
  String get noRouteFound => 'Pot ni bila najdena. Preverite povezavo.';

  @override
  String get acquiringGps => 'Pridobivanje GPS-a…';

  @override
  String get graphhopperServerNotConfigured =>
      'Strežnik GraphHopper ni konfiguriran — uporabljam OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'API-ključ GraphHopper ni konfiguriran — uporabljam OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'API-ključ OpenRouteService ni konfiguriran — uporabljam OSRM';

  @override
  String get chooseRoute => 'Izberite pot';

  @override
  String get cancel => 'Prekliči';

  @override
  String get startNavigation => 'Začni navigacijo';

  @override
  String get fastestRoute => 'Najhitrejša';

  @override
  String get now => 'Zdaj';

  @override
  String get history => 'Zgodovina';

  @override
  String get clearHistory => 'Počisti';

  @override
  String get selectedPosition => 'Izbrana lokacija';

  @override
  String get bottomBarNotifications => 'Obvestila';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Meni';

  @override
  String get notificationsTitle => 'Obvestila';

  @override
  String get notificationsEmpty => 'Zaenkrat ni obvestil';

  @override
  String get notificationsEmptyBody =>
      'Zapi in odzivi na vaše prometne prijave se bodo prikazali tukaj.';

  @override
  String get notificationsLoginRequired => 'Povežite svoj profil Nostr';

  @override
  String get notificationsLoginRequiredBody =>
      'Prijavite se z Amber ali nsec, da prejemate obvestila drugih uporabnikov.';

  @override
  String get settingsTitle => 'Nastavitve';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Zemljevid';

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
  String get cursorColorLabel => 'Barva';

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
  String get cursorColorViolet => 'Vijolična';

  @override
  String get cursorColorIndigo => 'Indigo';

  @override
  String get cursorColorBlue => 'Modra';

  @override
  String get cursorColorGreen => 'Zelena';

  @override
  String get cursorColorYellow => 'Rumena';

  @override
  String get cursorColorOrange => 'Oranžna';

  @override
  String get cursorColorRed => 'Rdeča';

  @override
  String get sectionInfo => 'Informacije';

  @override
  String get sectionLanguage => 'Jezik';

  @override
  String get themeLightNostr => 'Svetla · Nostr Vijolična';

  @override
  String get themeLightBitcoin => 'Svetla · Bitcoin Oranžna';

  @override
  String get themeDarkNostr => 'Temna · Nostr Vijolična';

  @override
  String get themeDarkBitcoin => 'Temna · Bitcoin Oranžna';

  @override
  String get langSystem => 'Sistemska privzeta';

  @override
  String get keepScreenOn => 'Ohrani zaslon prižgan';

  @override
  String get keepScreenOnDescription => 'Prepreči spanje med navigacijo';

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
  String get showAltitude => 'Prikaži nadmorsko višino';

  @override
  String get showAltitudeDescription =>
      'Majhen prikaz nadmorske višine v realnem času ob kontrolnikih zemljevida';

  @override
  String get autoCenterOnLaunch => 'Ob zagonu usredišči na mojo lokacijo';

  @override
  String get autoCenterOnLaunchDesc =>
      'GPS samodejno uporabi le, če je dovoljenje za lokacijo že odobreno';

  @override
  String get mapTileUrlLabel => 'URL ploščice zemljevida';

  @override
  String get routingProviderLabel => 'Ponudnik usmerjanja';

  @override
  String get osrmProvider => 'OSRM (javni, ključ ni potreben)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokalni/zasebni)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API-ključ)';

  @override
  String get openrouteProvider => 'OpenRouteService (API-ključ)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'API-ključ GraphHopper (neobvezno)';

  @override
  String get verify => 'Preveri';

  @override
  String get graphhopperServerUrlRequired =>
      'Vnesite URL strežnika pred preverjanjem.';

  @override
  String get successTitle => 'Uspešno';

  @override
  String get graphhopperServerReachable => 'Strežnik GraphHopper je dosegljiv';

  @override
  String get errorTitle => 'Napaka';

  @override
  String get close => 'Zapri';

  @override
  String get infoVersion => 'Različica';

  @override
  String get infoProtocol => 'Protokol';

  @override
  String get infoMaps => 'Zemljevidi';

  @override
  String get infoRouting => 'Usmerjanje';

  @override
  String get infoSource => 'Vir';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (samogostovan)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (oblak)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get profileVisibilityTitle => 'Vidnost profila';

  @override
  String get profileVisibilityDesc =>
      'Izberite, ali drugi uporabniki Roadstr vidijo vaše ime na Nostr, npub in sliko.';

  @override
  String get profileVisibilityClear => 'Prikaži profil javno';

  @override
  String get profileVisibilityPseudonymous => 'Psevdonimni način (priporočeno)';

  @override
  String get profileVisibilityPublishError =>
      'Izbira je bila shranjena lokalno, vendar je še ni bilo mogoče objaviti na Nostr.';

  @override
  String get nostrichLabel => 'nostrich';

  @override
  String get profileHiddenNotice => 'Ta uporabnik uporablja psevdonimni način.';

  @override
  String get editSpeedLimit => 'Dodaj ali posodobi omejitev hitrosti';

  @override
  String get requestSpeedLimit => 'Predlagaj omejitev hitrosti';

  @override
  String get acceptEditRequest => 'Sprejmi predlog';

  @override
  String get pendingEditRequests => 'Čakajoči predlogi sprememb';

  @override
  String get speedLimitHint => 'Omejitev hitrosti';

  @override
  String get speedLimitSaved => 'Omejitev hitrosti objavljena';

  @override
  String get editRequestSent => 'Predlog poslan avtorju prijave';

  @override
  String get notConnected => 'Nepovezano';

  @override
  String get loginWithNostrTitle => 'PRIJAVA Z NOSTROM';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Zasebni ključ nikoli ne zapusti vaše naprave · Priporočeno';

  @override
  String get nsecLoginOption => 'Vnesi nsec';

  @override
  String get nsecLoginDescription =>
      'Zasebni ključ shranjen lokalno · Manj varno';

  @override
  String get connectedViaAmber => 'Povezano prek Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Povezano prek nsec';

  @override
  String get publicKeyLabel => 'JAVNI KLJUČ';

  @override
  String get npubCopiedToClipboard => 'npub kopiran v odložišče';

  @override
  String get logoutTitle => 'Prekini povezavo';

  @override
  String get logoutConfirmation => 'Odstraniti poverilnice Nostr s te naprave?';

  @override
  String get logoutButton => 'Prekini povezavo';

  @override
  String get nostrIdentityInfo =>
      'Z identiteto Nostr lahko na decentraliziran način objavljate prometna opozorila, poročila in točke zanimanja v omrežju Nostr brez centralnih strežnikov.';

  @override
  String get warningTitle => 'Opozorilo';

  @override
  String get nsecWarning =>
      'Tik pred tem ste, da kar tako vtisnete vaš zasebni ključ Nostr neposredno v aplikacijo. Kdorkoli z fizičnim ali oddaljenim dostopom do vaše naprave ga lahko prebere in za vedno prevzame nadzor nad vašo identiteto Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  Varna metoda je Amber (NIP-55): nsec nikoli ne zapusti trezorja za podpisovanje aplikacij.';

  @override
  String get nsecRiskAcknowledgment => 'Razumem tveganje in želim nadaljevati';

  @override
  String get continueButton => 'Nadaljuj';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber je podpisnik aplikacij za Android, skladen z NIP-55. Vaš zasebni ključ ostane izoliran znotraj Amber in nikoli ni deljen.';

  @override
  String get requestKeyFromAmber => 'Zahtevaj javni ključ iz Amber';

  @override
  String get waitingForAmberResponse => 'Čakanje na odgovor Amber…';

  @override
  String get enterNsecTitle => 'Vnesi nsec';

  @override
  String get loginButton => 'Prijava';

  @override
  String get invalidNpubTitle => 'Neveljaven npub';

  @override
  String get invalidNsecTitle => 'Neveljaven nsec';

  @override
  String get invalidNsecMessage =>
      'Prepričajte se, da ste prilepili pravilen nsec.';

  @override
  String get ok => 'V redu';

  @override
  String get gpsNotActiveTitle => 'GPS ni aktiven';

  @override
  String get gpsDisabledMessage =>
      'Aktivirajte GPS v nastavitvah naprave, da pridobite svojo lokacijo in uporabljate navigacijo.';

  @override
  String get openSettings => 'Nastavitve';

  @override
  String get myLocation => 'Moja lokacija';

  @override
  String get loginToReport =>
      'Prijavite se z Nostrom (razdelek Profil) za poročanje o dogodkih';

  @override
  String get navigateHere => 'Navigiraj sem';

  @override
  String get reportEventHere => 'Poročaj o dogodku tukaj';

  @override
  String get zapSendSats => 'Zap ⚡ (pošlji sats)';

  @override
  String get sendZap => 'Pošlji Zap';

  @override
  String get chooseAmountSats => 'Izberite znesek v satoshijih (sats):';

  @override
  String get customAmount => 'Poljuben znesek…';

  @override
  String get zapSending => 'Pošiljanje…';

  @override
  String get fetchingLightningAddress => 'Pridobivanje Lightning naslova…';

  @override
  String get noLightningAddress => 'Ta poročevalec nima Lightning naslova';

  @override
  String get requestingInvoice => 'Zahtevanje računa…';

  @override
  String get lnurlUnavailable => 'LNURL ni na voljo';

  @override
  String get invoiceFailed => 'Računa ni mogoče ustvariti';

  @override
  String get openingWallet => 'Odpiranje denarnice…';

  @override
  String get payingViaNwc => 'Plačevanje prek NWC…';

  @override
  String get noLightningWallet => 'Lightning denarnica ni najdena';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats poslano!';
  }

  @override
  String get stillThere => 'Še vedno tam';

  @override
  String get notThereAnymore => 'Ni več tam';

  @override
  String get loginToConfirm =>
      'Prijavite se z Nostrom za potrditev ali izpodbijanje';

  @override
  String get reportAnEvent => 'Poročaj o dogodku';

  @override
  String get notifZapTitle => 'Prejet zap';

  @override
  String notifZapBody(int sat) {
    return 'Prejeli ste zap v vrednosti $sat satov!';
  }

  @override
  String get notifConfirmedTitle => 'Prijava potrjena';

  @override
  String notifConfirmedBody(String category) {
    return 'Vašo prijavo $category je potrdil drug voznik';
  }

  @override
  String get notifDeniedTitle => 'Prijava izpodbijana';

  @override
  String notifDeniedBody(String category) {
    return 'Nekdo je sporočil, da vašega $category ni več tam';
  }

  @override
  String chainedManeuver(String first, String second) {
    return '$first, nato $second';
  }

  @override
  String thenManeuver(String instruction) {
    return 'nato $instruction';
  }

  @override
  String get reportSpeedLimitHint => 'Omejitev hitrosti (neobvezno)';

  @override
  String get reportedSpeedLimit => 'Prijavljena omejitev hitrosti';

  @override
  String speedCameraVoiceAlert(int limit, String unit) {
    return 'Prijavljen radar, omejitev $limit $unit';
  }

  @override
  String get optionalComment => 'Neobvezni komentar…';

  @override
  String get publish => 'Objavi';

  @override
  String get publishing => 'Objavljanje…';

  @override
  String get reportPublished => 'Poročilo objavljeno ✓';

  @override
  String get myReports => 'MOJA POROČILA';

  @override
  String get noReportsYet => 'Ni objavljenih poročil';

  @override
  String get zapBalance => 'Zap stanje';

  @override
  String get satoshiFromReports => 'Satoshi, prejeti iz vaših poročil';

  @override
  String get reputationHigh => 'Visoka';

  @override
  String get reputationMedium => 'Srednja';

  @override
  String get reputationLow => 'Nizka';

  @override
  String reputationLabel(String level) {
    return 'Ugled $level';
  }

  @override
  String reliability(int pct) {
    return 'Zanesljivost: $pct%';
  }

  @override
  String get confirmedLabel => 'Potrjeno';

  @override
  String get removedLabel => 'Odstranjeno';

  @override
  String get positionLabel => 'Položaj';

  @override
  String get loadingLabel => 'Nalaganje…';

  @override
  String get sectionWebSearch => 'Spletno iskanje';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Prilepite vaš NWC URI (Alby Hub, Mutiny, Cashu…) za plačevanje Zapsov neposredno iz aplikacije.';

  @override
  String get searchEngineQwantDesc =>
      'Evropski, zasebnost na prvem mestu. Brez sledenja, brez oglaševalskih profilov. Priporočeno.';

  @override
  String get searchEngineBraveDesc =>
      'Neodvisni indeks, odprtokoden. Brez odvisnosti od Googla ali Binga. Nič profiliranja.';

  @override
  String get searchEngineDdgDesc =>
      'Osredotočen na zasebnost in priljubljen. Rezultati delno iz Binga — imejte to v mislih.';

  @override
  String get searchEngineStartpageDesc =>
      'Googlov rezultati brez predaje vaših podatkov Googlu. Razumni kompromis.';

  @override
  String get searchEngineGoogleDesc =>
      'Zelo učinkovito. A zapomnite si: Google vas pozna bolje kot vaša mama in vse prodaja oglaševalcem. Vaša odločitev. 🍪';

  @override
  String get categoryPolice => 'Policija';

  @override
  String get categorySpeedCamera => 'Radar za merjenje hitrosti';

  @override
  String get categoryTrafficJam => 'Prometni zamašek';

  @override
  String get categoryAccident => 'Prometna nesreča';

  @override
  String get categoryRoadClosure => 'Zapora ceste';

  @override
  String get categoryConstruction => 'Gradbena dela';

  @override
  String get categoryHazard => 'Nevarnost';

  @override
  String get categoryRoadCondition => 'Stanje ceste';

  @override
  String get categoryPothole => 'Udarna jama';

  @override
  String get categoryFog => 'Megla';

  @override
  String get categoryIce => 'Led';

  @override
  String get categoryAnimal => 'Žival';

  @override
  String get categoryOther => 'Drugo';

  @override
  String get dateTimeLabel => 'Datum / čas';

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
  String get sectionFavorites => 'Shranjene lokacije';

  @override
  String get addFavorite => 'Dodaj lokacijo';

  @override
  String get favoriteLabelHint => 'Ime (npr. Dom, Pisarna)';

  @override
  String get favoriteAddressHint => 'Naslov';

  @override
  String get favoriteGeocodingError =>
      'Naslov ni bil najden. Poskusi z natančnejšim naslovom.';

  @override
  String get trafficAlertTitle => 'Nov promet na poti';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category prijavljeno $age na vaši poti.\n\nAli želite poiskati alternativno pot?';
  }

  @override
  String get trafficContinue => 'Nadaljuj';

  @override
  String get trafficRecalculate => 'Preračunaj pot';

  @override
  String get navExitTitle => 'Izhod iz navigacije?';

  @override
  String get navExitBody =>
      'Ali želite ustaviti navigacijo in se vrniti na zemljevid?';

  @override
  String get navContinue => 'Nadaljuj navigacijo';

  @override
  String get navExit => 'Da, izhod';

  @override
  String get loadingInfo => 'Nalaganje informacij…';

  @override
  String get conditionsOnRoute => 'Pogoji na poti';

  @override
  String get calculateRoute => 'Izračunaj pot';

  @override
  String get sectionNavigationVoice => 'Glas navigacije';

  @override
  String get voiceGuidance => 'Glasovno vodenje';

  @override
  String get voiceGuidanceDesc =>
      'Med navigacijo na glas preberi navodila za zavijanje';

  @override
  String get voiceUnsupportedTitle => 'Glasovno vodenje ni na voljo';

  @override
  String get voiceUnsupportedBody =>
      'Vaš jezik še ni podprt za govorjena navodila za zavijanje. Navigacijska navodila se bodo še naprej prikazovala kot besedilo na zaslonu.';

  @override
  String get kokoroModelTitle => 'Glasovni model (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Ni preneseno · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Prenos...';

  @override
  String get kokoroModelStatusReady => 'Glasovni model je pripravljen';

  @override
  String get kokoroModelDownloadBtn => 'Prenesi';

  @override
  String get kokoroModelSupportedLangs =>
      'Podpira: italijanščina, angleščina, španščina, francoščina, japonščina, kitajščina, portugalščina';

  @override
  String get autoDarkMode => 'Samodejno temno ozadje';

  @override
  String get autoDarkModeDesc => 'Aktivira temno temo ob sončnem zahodu';

  @override
  String get settingsImperialUnits => 'Imperialne enote';

  @override
  String get settingsImperialUnitsDesc =>
      'Milje in čevlji namesto kilometrov in metrov';

  @override
  String get arrivedTitle => '🎉 Ste prispeli!';

  @override
  String get arrivalAhead => 'Prispeli boste na cilj';

  @override
  String get arrivalAheadLeft => 'Prispeli boste na cilj na levi strani';

  @override
  String get arrivalAheadRight => 'Prispeli boste na cilj na desni strani';

  @override
  String continueForDistance(String distance) {
    return 'nadaljujte naravnost $distance';
  }

  @override
  String get transportModeCar => 'Avto';

  @override
  String get transportModeWalk => 'Peš';

  @override
  String etaArrivalLabel(String time) {
    return 'Prih. $time';
  }

  @override
  String get supportRoadstr => 'Podprite Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address kopirano v odložišče';
  }

  @override
  String get disclaimerTitle => 'Pomembno obvestilo';

  @override
  String get disclaimerAccept => 'Prebral/a sem in sprejemam';

  @override
  String get disclaimerBody =>
      'Roadstr je eksperimentalna, odprtokodna, s strani skupnosti vzdrževana navigacijska aplikacija, ki temelji na podatkih OpenStreetMap in protokolu Nostr ter je na voljo za uporabo v kateri koli državi. S prenosom, namestitvijo ali uporabo te aplikacije uporabnik brezpogojno sprejme vse spodaj navedene pogoje, brez ozemeljske omejitve.\n\n🚗 VARNOST V CESTNEM PROMETU NA PRVEM MESTU\nVoznik mora vedno gledati na cesto ter upoštevati vse veljavne prometne predpise in postavljeno prometno signalizacijo, ki imata vedno prednost pred kakršnim koli navodilom aplikacije. Naprave nikoli ne upravljajte med vožnjo; pred odhodom jo pritrdite v odobreno, dobro vidno držalo in nikoli ne odvračajte pozornosti s ceste, da bi z njo upravljali, medtem ko je vozilo v gibanju.\n\n⚠️ PREVZEM TVEGANJA — PO VSEM SVETU\nZ uporabo aplikacije Roadstr, v kateri koli državi in po kateremkoli pravnem sistemu, uporabnik zavestno in prostovoljno prevzame VSA tveganja, povezana z njeno uporabo, vključno z, vendar ne omejeno na: prometne nesreče, telesne poškodbe, smrt, škodo na premoženju, škodo na vozilu, globe, upravne sankcije, odvoz vozila, zaseg vozila, kazensko odgovornost ali katero koli drugo posledico, ki neposredno ali posredno izhaja iz zanašanja na aplikacijo. Uporabnik sam v celoti odgovarja za vsako odločitev v zvezi z vožnjo in navigacijo.\n\n🚫 BREZ GARANCIJE\nRoadstr je na voljo izključno v stanju, „KAKRŠNO JE“, in „KOT JE NA VOLJO“, brez kakršnega koli jamstva, izrecnega, implicitnega ali zakonsko določenega — vključno z, brez omejitev, jamstvi glede točnosti, popolnosti, zanesljivosti, razpoložljivosti, primernosti za trg, primernosti za določen namen in nekršenja pravic. Podatki o zemljevidih, izračun poti, omejitve hitrosti, radarji za nadzor hitrosti in podatki o conah z omejenim prometom (ZTL/ZAC/LTZ) izvirajo iz odprtih, s strani skupnosti vzdrževanih virov (OpenStreetMap, Overpass API), ki so lahko za katero koli državo, regijo ali občino kadar koli in brez obvestila nepopolni, zastareli ali netočni. Uporabnik je izključno odgovoren, da pred potovanjem in med njim samostojno preveri zakonitost in prevoznost katere koli predlagane poti glede na uradno lokalno signalizacijo in predpise.\n\n📍 NATANČNOST IN GPS\nDoločanje položaja GPS je lahko netočno ali nedosegljivo. Vsa navodila, razdalje in opozorila so na voljo zgolj kot smernice in se nikoli ne smejo obravnavati kot edina podlaga za odločitev pri vožnji.\n\n🛡️ OMEJITEV ODGOVORNOSTI\nV največji meri, ki jo dopušča veljavna zakonodaja v kateri koli jurisdikciji, razvijalci, sodelavci in katera koli stran, vključena v ustvarjanje ali distribucijo aplikacije Roadstr, ne odgovarjajo za nikakršno neposredno, posredno, naključno, posledično, posebno, vzorčno ali kazensko škodo katere koli vrste — vključno s telesnimi poškodbami, smrtjo ali finančno izgubo — ki izhaja iz uporabe ali nezmožnosti uporabe aplikacije ali je z njo povezana, tudi če so bili obveščeni o možnosti nastanka take škode. Kadar določena jurisdikcija delno ali v celoti ne dovoljuje te omejitve, se odgovornost omeji na najmanjši obseg, ki ga zakon v tej jurisdikciji dovoljuje.\n\n🤝 ODŠKODNINA\nUporabnik soglaša, da bo razvijalce in sodelavce razbremenil in jih varoval pred vsakršnim zahtevkom, škodo, izgubo ali stroškom (vključno s pravnimi stroški), ki izhaja iz uporabnikove uporabe aplikacije ali kršitve teh pogojev.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ DELITEV DOLOČB\nČe se katera koli določba teh pogojev v določeni jurisdikciji izkaže za neizvršljivo, se ta določba omeji ali izloči v najmanjšem potrebnem obsegu, vse preostale določbe pa ostanejo v celoti veljavne in učinkovite.\n\nZ uporabo aplikacije Roadstr kjer koli na svetu uporabnik potrjuje, da je prebral, razumel in brezpogojno sprejel vse zgoraj navedene pogoje ter prevzema polno in celotno odgovornost — in vsa tveganja — za uporabo aplikacije in vse posledice, ki iz nje izhajajo.';

  @override
  String get readOnWikipedia => 'Beri na Wikipediji';

  @override
  String get openInBrowser => 'Odpri v brskalniku';

  @override
  String get wikipediaLoadFailed => 'Wikipedije ni bilo mogoče varno naložiti.';

  @override
  String get retry => 'Poskusi znova';

  @override
  String get poiDetailsFromOsm => 'Informacije iz OpenStreetMap';

  @override
  String get poiCategory => 'Kategorija';

  @override
  String get poiOperator => 'Upravljavec';

  @override
  String get poiCuisine => 'Kuhinja';

  @override
  String get poiWheelchairYes => 'Dostopno z invalidskim vozičkom';

  @override
  String get poiWheelchairLimited => 'Omejen dostop z invalidskim vozičkom';

  @override
  String get poiWheelchairNo => 'Ni dostopno z invalidskim vozičkom';

  @override
  String get poiContact => 'Stik';

  @override
  String get poiAddress => 'Naslov';

  @override
  String get poiWebsite => 'Spletno mesto';

  @override
  String get poiAccessPrivate => 'Zaseben dostop';

  @override
  String get poiAccessCustomers => 'Samo za stranke';

  @override
  String get poiAccessPermit => 'Potrebno dovoljenje';

  @override
  String get poiAccessNo => 'Brez javnega dostopa';

  @override
  String get poiAccessDestination => 'Dostop samo do cilja';

  @override
  String get poiLightningAccepted => 'Sprejema Lightning';

  @override
  String get poiBitcoinAccepted => 'Sprejema Bitcoin';

  @override
  String get poiParkingDetails => 'Parkiranje';

  @override
  String get poiParkingSurface => 'Na površini';

  @override
  String get poiParkingUnderground => 'Podzemno';

  @override
  String get poiParkingMultiStorey => 'Večnadstropno';

  @override
  String get poiParkingStreetSide => 'Ob ulici';

  @override
  String get poiParkingLane => 'Na ulici';

  @override
  String get poiParkingRooftop => 'Na strehi';

  @override
  String get poiFee => 'Pristojbina';

  @override
  String get poiFree => 'Brezplačno';

  @override
  String get poiPaid => 'Plačljivo';

  @override
  String get poiCapacity => 'Zmogljivost';

  @override
  String get poiMaxStay => 'Najdaljši čas parkiranja';

  @override
  String get poiPrice => 'Cena';

  @override
  String get poiChargingDetails => 'Polnjenje';

  @override
  String get poiConnectorType2 => 'Tip 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Dizel';

  @override
  String get poiPetrol95 => 'Bencin 95';

  @override
  String get poiSmokingAllowed => 'Kajenje dovoljeno';

  @override
  String get poiSmokingOutside => 'Kajenje zunaj';

  @override
  String get poiSmokingAreas => 'Prostori za kadilce';

  @override
  String get poiSmokeFree => 'Brez kajenja';

  @override
  String get poiOutdoorSeating => 'Zunanje sedenje';

  @override
  String get poiTakeaway => 'Za s seboj';

  @override
  String get poiTakeawayOnly => 'Samo za s seboj';

  @override
  String get gpsSignalLost => 'Izgubljen signal GPS';

  @override
  String get poiOpenNow => 'Zdaj odprto';

  @override
  String get poiClosedNow => 'Zaprto';

  @override
  String poiOpensAt(String when) {
    return 'Odpre: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Zapre: $when';
  }

  @override
  String searchOnEngine(String engine) {
    return 'Iskanje na $engine';
  }

  @override
  String get plannerFromHint => 'Od…';

  @override
  String get plannerToHint => 'Cilj…';

  @override
  String departEta(String dep, String arr) {
    return 'Odhod $dep  →  Prihod $arr';
  }

  @override
  String get modeCar => 'Avto';

  @override
  String get modeBike => 'Kolo';

  @override
  String get modeWalk => 'Peš';

  @override
  String windSpeed(String speed) {
    return 'veter $speed';
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
  String get weatherPartlyCloudy => 'Spremenljivo';

  @override
  String get weatherCloudy => 'Oblačno';

  @override
  String get weatherFog => 'Megla';

  @override
  String get weatherLightRain => 'Rahel dež';

  @override
  String get weatherRain => 'Dež';

  @override
  String get weatherSnow => 'Sneg';

  @override
  String get weatherShowers => 'Nalivi';

  @override
  String get weatherThunderstorm => 'Nevihta';

  @override
  String get ztlInsideWarning => 'Omejena prometna cona';

  @override
  String get onboardingAppSubtitle => 'Odprtokodna Nostr navigacija';

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
  String get onboardingGetStarted => 'Začni';

  @override
  String get onboardingNostrTitle => 'Nostr identiteta';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Povezano';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (priporočeno)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'Ključ nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Neveljaven ključ nsec — preverite in poskusite znova.';

  @override
  String get onboardingSkip => 'Preskoči za zdaj';

  @override
  String get onboardingContinue => 'Nadaljuj';

  @override
  String get onboardingEnterNsec => 'Vnesite ključ nsec';

  @override
  String get onboardingSetupTitle => 'Nastavi Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Lokacija';

  @override
  String get onboardingLocationGranted => 'Dostop do lokacije odobren';

  @override
  String get onboardingLocationRequired => 'Potrebno za GPS navigacijo';

  @override
  String get onboardingGrantButton => 'Dovoli';

  @override
  String get onboardingGrapheneTitle => 'Uporabniki GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

  @override
  String get onboardingVoiceTitle => 'AI glasovno vodenje (neobvezno)';

  @override
  String get onboardingVoiceReady => 'Glasovni model Kokoro je pripravljen';

  @override
  String get onboardingVoiceDownloading => 'Prenos glasovnega modela…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Prenesi';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Pripravljeni ste na navigacijo!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Gremo!';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Glas';

  @override
  String get kokoroVoiceFemale => 'Ženski';

  @override
  String get kokoroVoiceMale => 'Moški';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Moški glas za ta jezik ni na voljo';

  @override
  String get kokoroSpeedTitle => 'Hitrost govora';

  @override
  String get kokoroVolumeTitle => 'Glasnost govora';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Neobvezno: shranjene priljubljene se lahko sinhronizirajo med vašimi napravami prek relejev Nostr, šifrirane od konca do konca (NIP-44) z vašim lastnim ključem — releji vidijo le šifrirano besedilo in nihče razen vas ne more brati vsebine. Omogočite kadar koli v Nastavitvah.';

  @override
  String get profileVisibilityOnboarding =>
      'Roadstr vedno objavi javni ključ Nostr, ki je potreben za preverjanje prijave. V psevdonimnem načinu drugi uporabniki Roadstr vidijo le splošen avatar in »nostrich«; v javnem načinu vidijo tudi vaše ime na Nostr, npub in profilno sliko. Vaše prijave, lokacije, ugled in prejeti zapi ostanejo javni na Nostr.';

  @override
  String get parkingSaveHere => 'Shrani parkiranje tukaj';

  @override
  String get parkingLongPressConfirm => 'Shraniti to mesto kot parkiranje?';

  @override
  String get parkingMarkerTitle => 'Parkirno mesto';

  @override
  String get parkingNavigateHere => 'Navigiraj do parkiranja';

  @override
  String get parkingRemove => 'Odstrani parkiranje';

  @override
  String get parkingSavedSnack => 'Parkirno mesto shranjeno';

  @override
  String get parkingRemovedSnack => 'Parkirno mesto odstranjeno';

  @override
  String get exportFavoritesTitle => 'Izvozi priljubljene';

  @override
  String get exportFavoritesDesc =>
      'Shranite svoja priljubljena mesta v datoteko JSON, ki jo lahko varnostno kopirate ali prenesete na drugo napravo.';

  @override
  String get exportEncryptToggle => 'Šifriraj z geslom';

  @override
  String get exportPasswordHint => 'Geslo';

  @override
  String get exportButton => 'Izvozi';

  @override
  String get exportSuccessSnack => 'Priljubljene izvožene';

  @override
  String get exportFailedSnack => 'Izvoz ni uspel';

  @override
  String get importFavoritesTitle => 'Uvozi priljubljene';

  @override
  String get importPasswordPrompt => 'Ta datoteka je šifrirana — vnesite geslo';

  @override
  String importSuccessSnack(int n) {
    return 'Uvoženih $n priljubljenih';
  }

  @override
  String get importFailedSnack => 'Uvoz ni uspel — preverite datoteko in geslo';

  @override
  String get syncFavoritesTitle => 'Sinhroniziraj priljubljene';

  @override
  String get syncFavoritesDesc =>
      'Objavite svoje priljubljene, šifrirane od konca do konca, na svojih relejih Nostr, da vam sledijo na vseh napravah. Releji vidijo le šifrirano besedilo — nihče razen vas ne more brati vsebine.';

  @override
  String get syncNowButton => 'Pošlji na Nostr';

  @override
  String get syncPullButton => 'Prenesi z Nostra';

  @override
  String get syncPassphraseTitle =>
      'Šifrirna fraza za sinhronizacijo (neobvezno)';

  @override
  String get syncPassphraseDesc =>
      'Druga plast šifriranja za sinhronizirane priljubljene: tudi če bi bil vaš ključ Nostr ogrožen, posnetek na relejih brez te fraze ostane neberljiv. Vnesete jo enkrat na vsaki novi napravi. Pustite prazno za izklop.';

  @override
  String get syncCustomRelayTitle => 'Lasten rele (izbirno)';

  @override
  String get syncCustomRelayDesc =>
      'Dodajte rele, ki ga sami gostite ali plačujete, in priljubljene se bodo objavljale tudi tam. Javni releji ne obljubljajo ničesar o tem, kako dolgo hranijo podatke; vaš pa. Pustite prazno za uporabo le vgrajenih relejev.';

  @override
  String get syncCustomRelayInvalid =>
      'Neveljaven naslov releja. Začeti se mora z wss://';

  @override
  String get syncSuccessSnack => 'Priljubljene sinhronizirane';

  @override
  String get syncFailedSnack => 'Sinhronizacija ni uspela';

  @override
  String syncLastSyncLabel(String when) {
    return 'Zadnja sinhronizacija: $when';
  }

  @override
  String get syncNoIdentity =>
      'Prijavite se z Nostrom (Nastavitve → Profil), da omogočite sinhronizacijo';

  @override
  String get onboardingVpnNotice =>
      'Za največjo zasebnost — prijave se širijo po omrežju Nostr — je priporočljiva uporaba VPN-ja. Priporočena izbira je Mullvad, ki ga je mogoče plačati z Bitcoinom.';

  @override
  String get trafficNormal => 'Običajen promet';

  @override
  String get trafficModerate => 'Zmeren promet';

  @override
  String get trafficHeavy => 'Gost promet';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get avoidHighwaysAndTolls => 'Izogni se avtocestam in cestninam';

  @override
  String get avoidRouteUnavailable =>
      'Pot, ki bi se izognila avtocestam in cestninam, ni bila najdena.';

  @override
  String get avoidanceUnavoidableSection =>
      'Zmanjša avtoceste/cestnine · neizogiben odsek';

  @override
  String get nearbyTitle => 'V bližini';

  @override
  String get nearbyNeedsGps => 'Čakanje na GPS';

  @override
  String get nearbyNothingFound => 'V polmeru 5 km ni ničesar';

  @override
  String get nearbyFuel => 'Bencinska črpalka';

  @override
  String get nearbySupermarket => 'Supermarket';

  @override
  String get nearbyAtm => 'Bankomat';

  @override
  String get nearbyPharmacy => 'Lekarna';

  @override
  String get nearbyHospital => 'Bolnišnica';

  @override
  String get nearbyPolice => 'Policija';

  @override
  String get nearbyPostOffice => 'Pošta';

  @override
  String get nearbyParking => 'Parkirišče';

  @override
  String get nearbyCharging => 'Polnjenje';

  @override
  String get transportModeTransit => 'Prevoz';

  @override
  String get transitNoServiceHere =>
      'Ni podatkov o javnem prevozu za to območje';

  @override
  String get transitRequestFailed => 'Javnega prevoza ni bilo mogoče naložiti';

  @override
  String get transitRetry => 'Poskusi znova';

  @override
  String get transitWalkLeg => 'Peš';

  @override
  String get transitYourLocation => 'Vaša lokacija';

  @override
  String get transitDestination => 'Cilj';

  @override
  String get transitScheduledTimes =>
      'Časi izhajajo iz objavljenih voznih redov';

  @override
  String get transitDirect => 'Neposredno';

  @override
  String transitTransfers(int count) {
    return '$count prestopov';
  }

  @override
  String ztlNearRouteWarning(String street) {
    return 'Pozor: $street je območje omejenega prometa';
  }

  @override
  String get themeModernNostr => 'Sodobna · Nostr vijolična';

  @override
  String get themeModernBitcoin => 'Sodobna · Bitcoin oranžna';

  @override
  String get themeModernDarkNostr => 'Sodobna temna · Nostr vijolična';

  @override
  String get themeModernDarkBitcoin => 'Sodobna temna · Bitcoin oranžna';

  @override
  String get plannerAddStop => 'Dodaj postanek';

  @override
  String get plannerStopHint => 'Vmesni postanek';

  @override
  String get plannerStopsFull => 'Dodatnih postankov ni mogoče dodati';

  @override
  String plannerStopAdded(String place) {
    return 'Dodan postanek: $place';
  }
}
