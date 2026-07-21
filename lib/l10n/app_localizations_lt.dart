// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Lithuanian (`lt`).
class AppLocalizationsLt extends AppLocalizations {
  AppLocalizationsLt([String locale = 'lt']) : super(locale);

  @override
  String get searchHint => 'Kur norite vykti?';

  @override
  String get calculatingRoute => 'Skaičiuojamas maršrutas…';

  @override
  String get routeNotFoundTitle => 'Maršrutas nerastas';

  @override
  String get noRouteFound => 'Maršrutas nerastas. Patikrinkite ryšį.';

  @override
  String get emptyServerResponse =>
      'Tuščias serverio atsakymas. Patikrinkite ryšį.';

  @override
  String routeError(String error) {
    return 'Maršruto skaičiavimo klaida: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS nepasiekiamas — Nustatymai → Programa → Roadstr → Leidimai → Vieta';

  @override
  String get acquiringGps => 'Gaunamas GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopper serveris nesukonfigūruotas — naudojamas OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper API raktas nesukonfigūruotas — naudojamas OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService API raktas nesukonfigūruotas — naudojamas OSRM';

  @override
  String get chooseRoute => 'Pasirinkti maršrutą';

  @override
  String routeOptionsCount(int count) {
    return '$count parinktys';
  }

  @override
  String get cancel => 'Atšaukti';

  @override
  String get startNavigation => 'Pradėti navigaciją';

  @override
  String get fastestRoute => 'Greičiausias';

  @override
  String get now => 'Dabar';

  @override
  String get history => 'Istorija';

  @override
  String get clearHistory => 'Išvalyti';

  @override
  String get selectedPosition => 'Pasirinkta padėtis';

  @override
  String get bottomBarNotifications => 'Notifications';

  @override
  String get bottomBarProfile => 'Profilis';

  @override
  String get bottomBarMenu => 'Meniu';

  @override
  String get notificationsTitle => 'Notifications';

  @override
  String get notificationsEmpty => 'No notifications yet';

  @override
  String get notificationsEmptyBody =>
      'Zaps and reactions to your road reports will appear here.';

  @override
  String get notificationsLoginRequired => 'Connect your Nostr profile';

  @override
  String get notificationsLoginRequiredBody =>
      'Sign in with Amber or nsec to receive notifications from other users.';

  @override
  String get settingsTitle => 'Nustatymai';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Žemėlapis';

  @override
  String get sectionInfo => 'Informacija';

  @override
  String get sectionLanguage => 'Kalba';

  @override
  String get themeLightNostr => 'Šviesi · Nostr Violetinė';

  @override
  String get themeLightBitcoin => 'Šviesi · Bitcoin Oranžinė';

  @override
  String get themeDarkNostr => 'Tamsi · Nostr Violetinė';

  @override
  String get themeDarkBitcoin => 'Tamsi · Bitcoin Oranžinė';

  @override
  String get langSystem => 'Sistemos numatytoji';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Laikyti ekraną įjungtą';

  @override
  String get keepScreenOnDescription =>
      'Neleidžia įjungti miego režimo navigacijos metu';

  @override
  String get autoCenterOnLaunch => 'Paleidžiant centruoti ties mano vieta';

  @override
  String get autoCenterOnLaunchDesc =>
      'GPS automatiškai naudojamas tik jei vietos leidimas jau suteiktas';

  @override
  String get rotateMap => 'Žemėlapis seka kryptį';

  @override
  String get rotateMapDescription => 'Žemėlapis sukasi pagal vairavimo kryptį';

  @override
  String get mapTileUrlLabel => 'Žemėlapio plytelių URL';

  @override
  String get routingProviderLabel => 'Maršrutizavimo tiekėjas';

  @override
  String get osrmProvider => 'OSRM (viešas, raktas nereikalingas)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (vietinis/privatus)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API raktas)';

  @override
  String get openrouteProvider => 'OpenRouteService (API raktas)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API raktas (neprivalomas)';

  @override
  String get verify => 'Patikrinti';

  @override
  String get graphhopperServerUrlRequired =>
      'Prieš tikrinant įveskite serverio URL.';

  @override
  String get successTitle => 'Sėkmė';

  @override
  String get graphhopperServerReachable => 'GraphHopper serveris pasiekiamas';

  @override
  String get errorTitle => 'Klaida';

  @override
  String get close => 'Uždaryti';

  @override
  String get infoVersion => 'Versija';

  @override
  String get infoProtocol => 'Protokolas';

  @override
  String get infoMaps => 'Žemėlapiai';

  @override
  String get infoRouting => 'Maršrutizavimas';

  @override
  String get infoSource => 'Šaltinis';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted =>
      'GraphHopper (savarankiškai laikomas)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (debesija)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profilis';

  @override
  String get notConnected => 'Neprisijungta';

  @override
  String get loginWithNostrTitle => 'PRISIJUNGTI SU NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Privatus raktas niekada nepalieka jūsų įrenginio · Rekomenduojama';

  @override
  String get nsecLoginOption => 'Įvesti nsec';

  @override
  String get nsecLoginDescription =>
      'Privatus raktas saugomas vietoje · Mažiau saugi';

  @override
  String get connectedViaAmber => 'Prisijungta per Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Prisijungta per nsec';

  @override
  String get publicKeyLabel => 'VIEŠASIS RAKTAS';

  @override
  String get npubCopiedToClipboard => 'npub nukopijuotas į iškarpinę';

  @override
  String get logoutTitle => 'Atjungti';

  @override
  String get logoutConfirmation =>
      'Pašalinti Nostr kredencialus iš šio įrenginio?';

  @override
  String get logoutButton => 'Atjungti';

  @override
  String get nostrIdentityInfo =>
      'Turėdami Nostr tapatybę, galite skelbti eismo įspėjimus, ataskaitas ir lankytinas vietas decentralizuotai Nostr tinkle, be centrinių serverių.';

  @override
  String get warningTitle => 'Įspėjimas';

  @override
  String get nsecWarning =>
      'Ketinate įvesti savo Nostr privatų raktą tiesiai į programą. Bet kas, turintis fizinę ar nuotolinę prieigą prie jūsų įrenginio, galėtų jį perskaityti ir visam laikui perimti jūsų Nostr tapatybės kontrolę.';

  @override
  String get amberSecureMethodHint =>
      '✦  Saugus metodas yra Amber (NIP-55): nsec niekada nepalieka programos pasirašytojo saugyklos.';

  @override
  String get nsecRiskAcknowledgment =>
      'Suprantu riziką ir vis tiek noriu tęsti';

  @override
  String get continueButton => 'Tęsti';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber yra NIP-55 suderinama Android programų pasirašymo programa. Jūsų privatus raktas lieka izoliuotas Amber viduje ir niekada nėra dalinamas.';

  @override
  String get requestKeyFromAmber => 'Prašyti viešojo rakto iš Amber';

  @override
  String get amberNotFound =>
      'Amber nerastas. Įdiekite jį iš Play Store arba rankiniu būdu įveskite savo npub.';

  @override
  String get waitingForAmberResponse => 'Laukiama Amber atsakymo…';

  @override
  String get pasteNpubManually => 'Įklijuokite savo npub rankiniu būdu:';

  @override
  String get confirmNpub => 'Patvirtinti npub';

  @override
  String get enterNsecTitle => 'Įvesti nsec';

  @override
  String get loginButton => 'Prisijungti';

  @override
  String get invalidNpubTitle => 'Neteisingas npub';

  @override
  String get invalidNsecTitle => 'Neteisingas nsec';

  @override
  String get invalidNpubMessage =>
      'Įsitikinkite, kad įklijavote teisingą npub.';

  @override
  String get invalidNsecMessage =>
      'Įsitikinkite, kad įklijavote teisingą nsec.';

  @override
  String get amberResponseError => 'Amber atsakymo klaida';

  @override
  String get ok => 'Gerai';

  @override
  String get or => 'arba';

  @override
  String get gpsNotActiveTitle => 'GPS neaktyvus';

  @override
  String get gpsDisabledMessage =>
      'Įjunkite GPS įrenginio nustatymuose, kad gautumėte savo buvimo vietą ir naudotumėtės navigacija.';

  @override
  String get openSettings => 'Nustatymai';

  @override
  String get myLocation => 'Mano vieta';

  @override
  String get loginToReport =>
      'Prisijunkite su Nostr (Profilio skyrius), kad praneštumėte apie įvykius';

  @override
  String get navigateHere => 'Naviguoti čia';

  @override
  String get reportEventHere => 'Pranešti apie įvykį čia';

  @override
  String get zapSendSats => 'Zap ⚡ (siųsti sats)';

  @override
  String get sendZap => 'Siųsti Zap';

  @override
  String get chooseAmountSats => 'Pasirinkite sumą satoshi (sats):';

  @override
  String get customAmount => 'Pasirinktinė suma…';

  @override
  String get zapSending => 'Siunčiama…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Gaunamas Lightning adresas…';

  @override
  String get noLightningAddress => 'Šis pranešėjas neturi Lightning adreso';

  @override
  String get requestingInvoice => 'Prašoma sąskaitos…';

  @override
  String get lnurlUnavailable => 'LNURL nepasiekiamas';

  @override
  String get invoiceFailed => 'Nepavyko sugeneruoti sąskaitos';

  @override
  String get openingWallet => 'Atidaroma piniginė…';

  @override
  String get payingViaNwc => 'Mokama per NWC…';

  @override
  String get noLightningWallet => 'Lightning piniginė nerasta';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats išsiųsta!';
  }

  @override
  String get stillThere => 'Vis dar yra';

  @override
  String get notThereAnymore => 'Jau nebėra';

  @override
  String get loginToConfirm =>
      'Prisijunkite su Nostr, kad patvirtintumėte arba ginčytumėte';

  @override
  String get reportAnEvent => 'Pranešti apie įvykį';

  @override
  String get notifZapTitle => 'Gautas zap';

  @override
  String notifZapBody(int sat) {
    return 'Gavote $sat sat vertės zap!';
  }

  @override
  String get notifConfirmedTitle => 'Pranešimas patvirtintas';

  @override
  String notifConfirmedBody(String category) {
    return 'Jūsų $category pranešimą patvirtino kitas vairuotojas';
  }

  @override
  String get notifDeniedTitle => 'Pranešimas ginčijamas';

  @override
  String notifDeniedBody(String category) {
    return 'Kažkas pranešė, kad jūsų $category jau nebėra';
  }

  @override
  String chainedManeuver(String first, String second) {
    return '$first, tada $second';
  }

  @override
  String get reportSpeedLimitHint => 'Greičio apribojimas (nebūtina)';

  @override
  String get reportedSpeedLimit => 'Praneštas greičio apribojimas';

  @override
  String speedCameraVoiceAlert(int limit, String unit) {
    return 'Greičio kamera, apribojimas $limit $unit';
  }

  @override
  String get optionalComment => 'Neprivalomas komentaras…';

  @override
  String get publish => 'Skelbti';

  @override
  String get publishing => 'Skelbiama…';

  @override
  String get reportPublished => 'Ataskaita paskelbta ✓';

  @override
  String get myReports => 'MANO ATASKAITOS';

  @override
  String get noReportsYet => 'Nėra paskelbtų ataskaitų';

  @override
  String get zapBalance => 'Zap balansas';

  @override
  String get satoshiFromReports => 'Satoshi gauti iš jūsų ataskaitų';

  @override
  String get reputationHigh => 'Aukšta';

  @override
  String get reputationMedium => 'Vidutinė';

  @override
  String get reputationLow => 'Žema';

  @override
  String reputationLabel(String level) {
    return 'Reputacija $level';
  }

  @override
  String reliability(int pct) {
    return 'Patikimumas: $pct%';
  }

  @override
  String get confirmedLabel => 'Patvirtinta';

  @override
  String get removedLabel => 'Pašalinta';

  @override
  String get positionLabel => 'Pozicija';

  @override
  String get loadingLabel => 'Kraunama…';

  @override
  String get sectionWebSearch => 'Interneto paieška';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Įklijuokite savo NWC URI (Alby Hub, Mutiny, Cashu…), kad mokėtumėte Zaps tiesiogiai iš programos.';

  @override
  String get searchEngineQwantDesc =>
      'Europietiška, privatumas pirmiausia. Jokio sekimo, jokių reklaminių profilių. Rekomenduojama.';

  @override
  String get searchEngineBraveDesc =>
      'Nepriklausomas indeksas, atvirojo kodo. Jokios priklausomybės nuo Google ar Bing. Nulis profiliavimo.';

  @override
  String get searchEngineDdgDesc =>
      'Orientuota į privatumą ir populiari. Rezultatai iš dalies iš Bing — turėkite tai omenyje.';

  @override
  String get searchEngineStartpageDesc =>
      'Google rezultatai neperduodant jūsų duomenų Google. Protingas kompromisas.';

  @override
  String get searchEngineGoogleDesc =>
      'Labai efektyvi. Bet prisiminkite: Google pažįsta jus geriau nei jūsų mama ir parduoda viską reklamuotojams. Jūsų pasirinkimas. 🍪';

  @override
  String get categoryPolice => 'Policija';

  @override
  String get categorySpeedCamera => 'Greičio matuoklis';

  @override
  String get categoryTrafficJam => 'Spūstis';

  @override
  String get categoryAccident => 'Avarija';

  @override
  String get categoryRoadClosure => 'Kelio uždarymas';

  @override
  String get categoryConstruction => 'Kelių darbai';

  @override
  String get categoryHazard => 'Pavojus';

  @override
  String get categoryRoadCondition => 'Kelio būklė';

  @override
  String get categoryPothole => 'Duobė';

  @override
  String get categoryFog => 'Rūkas';

  @override
  String get categoryIce => 'Ledas';

  @override
  String get categoryAnimal => 'Gyvūnas';

  @override
  String get categoryOther => 'Kita';

  @override
  String get dateTimeLabel => 'Data / laikas';

  @override
  String minutesAgo(int count) {
    return 'prieš $count min';
  }

  @override
  String hoursAgo(int count) {
    return 'prieš ${count}h';
  }

  @override
  String daysAgo(int count) {
    return 'prieš ${count}d';
  }

  @override
  String get sectionFavorites => 'Išsaugotos vietos';

  @override
  String get addFavorite => 'Pridėti vietą';

  @override
  String get favoriteLabelHint => 'Pavadinimas (pvz. Namai, Biuras)';

  @override
  String get favoriteAddressHint => 'Adresas';

  @override
  String get favoriteGeocodingError =>
      'Adresas nerastas. Bandyk tikslesnį adresą.';

  @override
  String get trafficAlertTitle => 'Naujas eismas maršrute';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category pranešta $age jūsų maršrute.\n\nAr norite rasti alternatyvų maršrutą?';
  }

  @override
  String get trafficContinue => 'Tęsti';

  @override
  String get trafficRecalculate => 'Perskaičiuoti maršrutą';

  @override
  String get navExitTitle => 'Išeiti iš navigacijos?';

  @override
  String get navExitBody =>
      'Ar norite sustabdyti navigaciją ir grįžti į žemėlapį?';

  @override
  String get navContinue => 'Tęsti navigaciją';

  @override
  String get navExit => 'Taip, išeiti';

  @override
  String get loadingInfo => 'Įkeliama informacija…';

  @override
  String get conditionsOnRoute => 'Sąlygos maršrute';

  @override
  String get calculateRoute => 'Apskaičiuoti maršrutą';

  @override
  String get sectionNavigationVoice => 'Navigacijos balsas';

  @override
  String get voiceGuidance => 'Balso nukreipimas';

  @override
  String get voiceGuidanceDesc =>
      'Skaityti posūkio nurodymus garsiai navigacijos metu';

  @override
  String get testVoiceEngine => 'Išbandyti balso variklį';

  @override
  String get testVoiceEngineDesc =>
      'Palieskite, kad patikrintumėte TTS variklį ir gautumėte nustatymo instrukcijas';

  @override
  String get ttsDialogTitle => 'Trūksta balso variklio';

  @override
  String get ttsDialogBody =>
      'Nerasta veikiančio Text-to-Speech variklio.\n\n„Roadstr“ remiasi tik atvirojo kodo programine įranga — įdiekite vieną iš šių nemokamų variklių iš F-Droid:';

  @override
  String get ttsRhvoiceDesc =>
      'Natūraliai skambantis balsas, ribotas kalbų sąrašas';

  @override
  String get ttsEspeakDesc => 'Apima daugiau nei 100 kalbų, robotiškas balsas';

  @override
  String get ttsInstallNote =>
      '⚠️ Įdiegus:\n1. Android nustatymai → Pritaikymas neįgaliesiems → Teksto į kalbą keitiklis\n2. Pasirinkite ką tik įdiegtą variklį\n3. Atsisiųskite savo kalbos balso duomenis\n4. Visiškai paleiskite „Roadstr“ iš naujo';

  @override
  String get ttsTestNow => 'Išbandyti dabar';

  @override
  String get voiceUnsupportedTitle => 'Balso nukreipimas nepasiekiamas';

  @override
  String get voiceUnsupportedBody =>
      'Jūsų kalba dar nepalaikoma balsu skelbiamiems posūkio nurodymams. Navigacijos nurodymai ir toliau bus rodomi kaip tekstas ekrane.';

  @override
  String get kokoroModelTitle => 'Balso modelis (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Neparsisiųsta · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Atsisiunčiama...';

  @override
  String get kokoroModelStatusReady => 'Balso modelis paruoštas';

  @override
  String get kokoroModelDownloadBtn => 'Atsisiųsti';

  @override
  String get kokoroModelSupportedLangs =>
      'Palaiko: italų, anglų, ispanų, prancūzų, japonų, kinų, portugalų';

  @override
  String get autoDarkMode => 'Automatinė tamsi tema';

  @override
  String get autoDarkModeDesc => 'Aktyvuoja tamsią temą saulėlydžio metu';

  @override
  String get settingsImperialUnits => 'Imperiniai vienetai';

  @override
  String get settingsImperialUnitsDesc =>
      'Mylios ir pėdos vietoj kilometrų ir metrų';

  @override
  String get arrivedTitle => '🎉 Atvykote!';

  @override
  String get arrivedBody => 'Pasiekėte savo kelionės tikslą.';

  @override
  String get arrivedFeedbackPrompt => 'Kaip sekėsi?';

  @override
  String get feedbackBad => 'Blogai';

  @override
  String get feedbackGood => 'Gerai!';

  @override
  String get feedbackDialogTitle => 'Pasakykite mums, kas nutiko ne taip';

  @override
  String get feedbackHint => 'Aprašykite problemą…';

  @override
  String get feedbackSent => 'Atsiliepimas išsiųstas — ačiū! 🙏';

  @override
  String get feedbackSubmit => 'Siųsti';

  @override
  String get transportModeCar => 'Automobilis';

  @override
  String get transportModeWalk => 'Pėsčiomis';

  @override
  String etaArrivalLabel(String time) {
    return 'Atvyk. $time';
  }

  @override
  String get supportRoadstr => 'Paremkite Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address nukopijuota į iškarpinę';
  }

  @override
  String get disclaimerTitle => 'Svarbus pranešimas';

  @override
  String get disclaimerAccept => 'Perskaičiau ir sutinku';

  @override
  String get disclaimerBody =>
      'Roadstr yra eksperimentinė, atvirojo kodo, bendruomenės prižiūrima navigacijos programėlė, pagrįsta OpenStreetMap duomenimis ir Nostr protokolu, prieinama naudoti bet kurioje šalyje. Atsisiųsdamas, įdiegdamas ar naudodamas šią programėlę, naudotojas besąlygiškai sutinka su visomis toliau nurodytomis sąlygomis, be jokių teritorinių apribojimų.\n\n🚗 KELIŲ EISMO SAUGUMAS PIRMIAUSIA\nVairuotojas visada privalo žiūrėti į kelią ir laikytis visų taikomų kelių eismo taisyklių bei kelio ženklų, kurie visada turi viršenybę prieš bet kokią programėlės nuorodą. Niekada nevaldykite įrenginio vairuodami; prieš pradėdami važiuoti pritvirtinkite jį patvirtintame, gerai matomame laikiklyje ir niekada nenukreipkite dėmesio nuo kelio, kad sąveikautumėte su juo transporto priemonei judant.\n\n⚠️ RIZIKOS PRISIĖMIMAS — VISAME PASAULYJE\nNaudodamasis „Roadstr“ bet kurioje šalyje ir pagal bet kokią teisinę sistemą, naudotojas sąmoningai ir savanoriškai prisiima VISĄ riziką, susijusią su jos naudojimu, įskaitant, bet neapsiribojant: eismo įvykius, kūno sužalojimus, mirtį, turtinę žalą, transporto priemonės apgadinimą, baudas, administracines nuobaudas, evakavimą, transporto priemonės areštą, baudžiamąją atsakomybę arba bet kokias kitas pasekmes, tiesiogiai ar netiesiogiai kylančias iš pasitikėjimo programėle. Naudotojas vienas prisiima visą atsakomybę už kiekvieną vairavimo ir navigacijos sprendimą.\n\n🚫 GARANTIJŲ NETEIKIMAS\n„Roadstr“ teikiama griežtai tokia, „KOKIA YRA“ ir „KOKIA PRIEINAMA“, be jokios rūšies garantijų, nei tiesioginių, nei numanomų, nei įstatymų numatytų — įskaitant, be apribojimų, tikslumo, išsamumo, patikimumo, prieinamumo, tinkamumo parduoti, tinkamumo konkrečiam tikslui ir teisių nepažeidimo garantijas. Žemėlapių duomenys, maršrutų sudarymas, greičio apribojimai, greičio matuokliai ir eismo ribojimo zonų (ZTL/ZAC/LTZ) informacija gaunami iš atvirų, bendruomenės prižiūrimų šaltinių (OpenStreetMap, Overpass API), kurie gali būti neišsamūs, pasenę ar netikslūs bet kurioje šalyje, regione ar savivaldybėje, bet kuriuo metu ir be išankstinio įspėjimo. Naudotojas vienintelis atsako už tai, kad prieš kelionę ir jos metu savarankiškai patikrintų bet kokio siūlomo maršruto teisėtumą ir pravažiuojamumą, palygindamas juos su oficialiais vietos kelio ženklais ir teisės aktais.\n\n📍 TIKSLUMAS IR GPS\nGPS padėties nustatymas gali būti netikslus arba nepasiekiamas. Visos kryptys, atstumai ir įspėjimai pateikiami tik orientaciniais tikslais ir niekada neturėtų būti laikomi vienintele vairavimo sprendimo pagrindu.\n\n🛡️ ATSAKOMYBĖS APRIBOJIMAS\nMaksimalia taikytinos teisės leidžiama apimtimi bet kurioje jurisdikcijoje, kūrėjai, prisidėjusieji asmenys ir bet kuri šalis, dalyvavusi kuriant ar platinant „Roadstr“, neatsako už jokią tiesioginę, netiesioginę, atsitiktinę, pasekminę, specialią, pavyzdinę ar baudinę žalą, įskaitant kūno sužalojimą, mirtį ar finansinius nuostolius, kylančią iš programėlės naudojimo ar negalėjimo ja naudotis arba su tuo susijusią, net jei buvo įspėti apie tokios žalos galimybę. Jei tam tikroje jurisdikcijoje šis apribojimas iš dalies ar visiškai neleidžiamas, atsakomybė apribojama iki mažiausios toje jurisdikcijoje teisės aktais leidžiamos apimties.\n\n🤝 ŽALOS ATLYGINIMAS\nNaudotojas sutinka atlyginti žalą ir apsaugoti kūrėjus bei prisidėjusius asmenis nuo bet kokių pretenzijų, žalos, nuostolių ar išlaidų (įskaitant teisines išlaidas), kylančių dėl naudotojo naudojimosi programėle ar šių sąlygų pažeidimo.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ ATSKIRIAMUMAS\nJei kuri nors šių sąlygų nuostata tam tikroje jurisdikcijoje pripažįstama neįgyvendinama, ta nuostata apribojama arba pašalinama minimaliai būtina apimtimi, o visos likusios nuostatos išlieka galioti visa apimtimi.\n\nNaudodamasis „Roadstr“ bet kurioje pasaulio vietoje, naudotojas patvirtina, kad perskaitė, suprato ir besąlygiškai priėmė visas pirmiau nurodytas sąlygas, ir prisiima visą ir besąlygišką atsakomybę — bei visą riziką — už programėlės naudojimą ir bet kokias iš to kylančias pasekmes.';

  @override
  String get readOnWikipedia => 'Skaityti Vikipedijoje';

  @override
  String get openInBrowser => 'Atidaryti naršyklėje';

  @override
  String get wikipediaLoadFailed => 'Nepavyko saugiai įkelti Vikipedijos.';

  @override
  String get retry => 'Bandyti dar kartą';

  @override
  String get poiDetailsFromOsm => 'Informacija iš OpenStreetMap';

  @override
  String get poiCategory => 'Kategorija';

  @override
  String get poiOperator => 'Operatorius';

  @override
  String get poiCuisine => 'Virtuvė';

  @override
  String get poiAccessibility => 'Prieinamumas';

  @override
  String get poiWheelchairYes => 'Prieinama neįgaliojo vežimėliu';

  @override
  String get poiWheelchairLimited =>
      'Ribotas pritaikymas neįgaliojo vežimėliui';

  @override
  String get poiWheelchairNo => 'Neprieinama neįgaliojo vežimėliu';

  @override
  String get poiContact => 'Kontaktai';

  @override
  String get poiAddress => 'Adresas';

  @override
  String get poiWebsite => 'Svetainė';

  @override
  String get poiAccessPrivate => 'Privati prieiga';

  @override
  String get poiAccessCustomers => 'Tik klientams';

  @override
  String get poiAccessPermit => 'Reikalingas leidimas';

  @override
  String get poiAccessNo => 'Viešos prieigos nėra';

  @override
  String get poiAccessDestination => 'Prieiga tik į paskirties vietą';

  @override
  String get poiLightningAccepted => 'Priima Lightning';

  @override
  String get poiBitcoinAccepted => 'Priima Bitcoin';

  @override
  String get poiParkingDetails => 'Stovėjimo aikštelė';

  @override
  String get poiParkingSurface => 'Antžeminė';

  @override
  String get poiParkingUnderground => 'Požeminė';

  @override
  String get poiParkingMultiStorey => 'Daugiaaukštė';

  @override
  String get poiParkingStreetSide => 'Šalia gatvės';

  @override
  String get poiParkingLane => 'Gatvėje';

  @override
  String get poiParkingRooftop => 'Ant stogo';

  @override
  String get poiFee => 'Mokestis';

  @override
  String get poiFree => 'Nemokama';

  @override
  String get poiPaid => 'Mokama';

  @override
  String get poiCapacity => 'Talpa';

  @override
  String get poiMaxStay => 'Ilgiausia stovėjimo trukmė';

  @override
  String get poiPrice => 'Kaina';

  @override
  String get poiChargingDetails => 'Įkrovimas';

  @override
  String get poiConnectorType2 => '2 tipas';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Dyzelinas';

  @override
  String get poiPetrol95 => 'Benzinas 95';

  @override
  String get poiSmokingAllowed => 'Rūkyti leidžiama';

  @override
  String get poiSmokingOutside => 'Rūkymas lauke';

  @override
  String get poiSmokingAreas => 'Rūkymo zonos';

  @override
  String get poiSmokeFree => 'Nerūkoma';

  @override
  String get poiOutdoorSeating => 'Lauko sėdimos vietos';

  @override
  String get poiTakeaway => 'Išsinešti';

  @override
  String get poiTakeawayOnly => 'Tik išsinešti';

  @override
  String get gpsSignalLost => 'Prarastas GPS signalas';

  @override
  String get poiOpenNow => 'Dabar atidaryta';

  @override
  String get poiClosedNow => 'Uždaryta';

  @override
  String poiOpensAt(String when) {
    return 'Atsidaro: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Užsidaro: $when';
  }

  @override
  String searchOnEngine(String engine) {
    return 'Ieškoti $engine';
  }

  @override
  String get plannerFromHint => 'Iš kur…';

  @override
  String get plannerToHint => 'Kelionės tikslas…';

  @override
  String departEta(String dep, String arr) {
    return 'Išvykimas $dep  →  Atvykimas $arr';
  }

  @override
  String get modeCar => 'Automobilis';

  @override
  String get modeBike => 'Dviratis';

  @override
  String get modeWalk => 'Pėsčiomis';

  @override
  String windSpeed(String speed) {
    return 'vėjas $speed';
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
  String get weatherClear => 'Giedra';

  @override
  String get weatherPartlyCloudy => 'Kintamas';

  @override
  String get weatherCloudy => 'Debesuota';

  @override
  String get weatherFog => 'Rūkas';

  @override
  String get weatherLightRain => 'Lengvas lietus';

  @override
  String get weatherRain => 'Lietus';

  @override
  String get weatherSnow => 'Sniegas';

  @override
  String get weatherShowers => 'Liūtys';

  @override
  String get weatherThunderstorm => 'Perkūnija';

  @override
  String get ztlAheadWarning =>
      'Priekyje riboto eismo zona — maršrutas eina per ją';

  @override
  String get ztlInsideWarning => 'Riboto eismo zona';

  @override
  String get onboardingAppSubtitle => 'Atvirojo kodo Nostr navigacija';

  @override
  String get onboardingWelcomeTitle => 'Sveiki';

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
  String get onboardingGetStarted => 'Pradėti';

  @override
  String get onboardingNostrTitle => 'Nostr tapatybė';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Prisijungta';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (rekomenduojama)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec raktas';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Neteisingas nsec raktas — patikrinkite ir bandykite dar kartą.';

  @override
  String get onboardingSkip => 'Praleisti kol kas';

  @override
  String get onboardingContinue => 'Tęsti';

  @override
  String get onboardingEnterNsec => 'Įveskite nsec raktą';

  @override
  String get onboardingSetupTitle => 'Nustatyti Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Vieta';

  @override
  String get onboardingLocationGranted => 'Prieiga prie vietos suteikta';

  @override
  String get onboardingLocationRequired => 'Reikalinga GPS navigacijai';

  @override
  String get onboardingGrantButton => 'Suteikti';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS naudotojai';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

  @override
  String get onboardingVoiceTitle => 'AI balso gidavimas (neprivaloma)';

  @override
  String get onboardingVoiceReady => 'Kokoro balso modelis paruoštas';

  @override
  String get onboardingVoiceDownloading => 'Atsisiunčiamas balso modelis…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Atsisiųsti';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Esate pasiruošę naviguoti!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Važiuojame!';

  @override
  String get onboardingProfileLoading => 'Profilis įkeliamas…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Balsas';

  @override
  String get kokoroVoiceFemale => 'Moteriškas';

  @override
  String get kokoroVoiceMale => 'Vyriškas';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Vyriškas balsas šiai kalbai nepasiekiamas';

  @override
  String get kokoroSpeedTitle => 'Kalbos greitis';

  @override
  String get kokoroVolumeTitle => 'Balso garsumas';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Neprivaloma: išsaugoti mėgstamiausi gali būti sinchronizuojami tarp jūsų įrenginių per Nostr reles, šifruojant nuo galo iki galo (NIP-44) jūsų pačių raktu — relės mato tik šifruotą tekstą ir niekas, išskyrus jus, negali skaityti turinio. Įjunkite bet kada Nustatymuose.';

  @override
  String get parkingSaveHere => 'Išsaugoti stovėjimą čia';

  @override
  String get parkingMarkerTitle => 'Stovėjimo vieta';

  @override
  String get parkingNavigateHere => 'Naviguoti į stovėjimo vietą';

  @override
  String get parkingRemove => 'Pašalinti stovėjimą';

  @override
  String get parkingSavedSnack => 'Stovėjimo vieta išsaugota';

  @override
  String get parkingRemovedSnack => 'Stovėjimo vieta pašalinta';

  @override
  String get exportFavoritesTitle => 'Eksportuoti mėgstamiausius';

  @override
  String get exportFavoritesDesc =>
      'Išsaugokite mėgstamiausias vietas JSON faile, kurį galite pasidaryti atsarginę kopiją arba perkelti į kitą įrenginį.';

  @override
  String get exportEncryptToggle => 'Šifruoti slaptažodžiu';

  @override
  String get exportPasswordHint => 'Slaptažodis';

  @override
  String get exportButton => 'Eksportuoti';

  @override
  String get exportSuccessSnack => 'Mėgstamiausi eksportuoti';

  @override
  String get exportFailedSnack => 'Eksportuoti nepavyko';

  @override
  String get importFavoritesTitle => 'Importuoti mėgstamiausius';

  @override
  String get importPasswordPrompt =>
      'Šis failas užšifruotas — įveskite slaptažodį';

  @override
  String importSuccessSnack(int n) {
    return 'Importuota mėgstamiausių: $n';
  }

  @override
  String get importFailedSnack =>
      'Importuoti nepavyko — patikrinkite failą ir slaptažodį';

  @override
  String get syncFavoritesTitle => 'Sinchronizuoti mėgstamiausius';

  @override
  String get syncFavoritesDesc =>
      'Publikuokite savo mėgstamiausius, šifruotus nuo galo iki galo, savo Nostr relėse, kad jie sektų jus visuose įrenginiuose. Relės mato tik šifruotą tekstą — niekas, išskyrus jus, negali skaityti turinio.';

  @override
  String get syncNowButton => 'Siųsti į Nostr';

  @override
  String get syncPullButton => 'Gauti iš Nostr';

  @override
  String get syncPassphraseTitle => 'Sinchronizavimo slaptafrazė (neprivaloma)';

  @override
  String get syncPassphraseDesc =>
      'Antras sinchronizuojamų mėgstamiausių šifravimo sluoksnis: net jei jūsų Nostr raktas būtų pavogtas, momentinė kopija relėse be šios slaptafrazės lieka neperskaitoma. Ją įvesite po kartą kiekviename naujame įrenginyje. Palikite tuščią, kad išjungtumėte.';

  @override
  String get syncSuccessSnack => 'Mėgstamiausi sinchronizuoti';

  @override
  String get syncFailedSnack => 'Sinchronizuoti nepavyko';

  @override
  String syncLastSyncLabel(String when) {
    return 'Paskutinį kartą sinchronizuota: $when';
  }

  @override
  String get syncNoIdentity =>
      'Prisijunkite su Nostr (Nustatymai → Profilis), kad įjungtumėte sinchronizavimą';

  @override
  String get onboardingVpnNotice =>
      'Maksimaliam privatumui — pranešimai platinami Nostr tinkle — rekomenduojama naudoti VPN. Rekomenduojamas pasirinkimas — Mullvad, už kurį galima mokėti bitkoinais.';

  @override
  String get trafficNormal => 'Įprastas eismas';

  @override
  String get trafficModerate => 'Vidutinis eismas';

  @override
  String get trafficHeavy => 'Intensyvus eismas';

  @override
  String get avoidHighwaysChip => 'Vengti greitkelių';

  @override
  String get avoidTollsChip => 'Vengti mokamų kelių';

  @override
  String get preferShorterChip => 'Trumpiausias maršrutas';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Rodyti maršruto peržiūrą';

  @override
  String get avoidHighwaysAndTolls => 'Vengti greitkelių ir mokamų kelių';

  @override
  String get avoidRouteUnavailable =>
      'Maršruto, vengiančio ir greitkelių, ir mokamų kelių, rasti nepavyko.';

  @override
  String get avoidanceUnavoidableSection =>
      'Mažina greitkelius/mokamus kelius · neišvengiama atkarpa';
}
