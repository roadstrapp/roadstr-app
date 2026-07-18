// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Danish (`da`).
class AppLocalizationsDa extends AppLocalizations {
  AppLocalizationsDa([String locale = 'da']) : super(locale);

  @override
  String get searchHint => 'Hvor vil du hen?';

  @override
  String get calculatingRoute => 'Beregner rute…';

  @override
  String get routeNotFoundTitle => 'Rute ikke fundet';

  @override
  String get noRouteFound => 'Ingen rute fundet. Tjek din forbindelse.';

  @override
  String get emptyServerResponse => 'Tomt serversvar. Tjek din forbindelse.';

  @override
  String routeError(String error) {
    return 'Fejl ved ruteberegning: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS utilgængeligt — Indstillinger → App → Roadstr → Tilladelser → Placering';

  @override
  String get acquiringGps => 'Henter GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopper-server ikke konfigureret — bruger OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper API-nøgle ikke konfigureret — bruger OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService API-nøgle ikke konfigureret — bruger OSRM';

  @override
  String get chooseRoute => 'Vælg rute';

  @override
  String routeOptionsCount(int count) {
    return '$count muligheder';
  }

  @override
  String get cancel => 'Annuller';

  @override
  String get startNavigation => 'Start navigation';

  @override
  String get fastestRoute => 'Hurtigste';

  @override
  String get now => 'Nu';

  @override
  String get history => 'Historik';

  @override
  String get clearHistory => 'Ryd';

  @override
  String get selectedPosition => 'Valgt position';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Menu';

  @override
  String get settingsTitle => 'Indstillinger';

  @override
  String get sectionTheme => 'Tema';

  @override
  String get sectionMap => 'Kort';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Sprog';

  @override
  String get themeLightNostr => 'Lyst · Nostr Violet';

  @override
  String get themeLightBitcoin => 'Lyst · Bitcoin Orange';

  @override
  String get themeDarkNostr => 'Mørkt · Nostr Violet';

  @override
  String get themeDarkBitcoin => 'Mørkt · Bitcoin Orange';

  @override
  String get langSystem => 'Systemstandard';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Hold skærmen tændt';

  @override
  String get keepScreenOnDescription => 'Forhindrer dvale under navigation';

  @override
  String get autoCenterOnLaunch => 'Centrer på min position ved opstart';

  @override
  String get autoCenterOnLaunchDesc =>
      'Bruger kun GPS automatisk, hvis placeringstilladelsen allerede er givet';

  @override
  String get rotateMap => 'Kortet følger retningen';

  @override
  String get rotateMapDescription => 'Kortet roterer baseret på køreretning';

  @override
  String get mapTileUrlLabel => 'Kortbrikke-URL';

  @override
  String get routingProviderLabel => 'Ruteudbyder';

  @override
  String get osrmProvider => 'OSRM (offentlig, ingen nøgle påkrævet)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokal/privat)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API-nøgle)';

  @override
  String get openrouteProvider => 'OpenRouteService (API-nøgle)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API-nøgle (valgfrit)';

  @override
  String get verify => 'Bekræft';

  @override
  String get graphhopperServerUrlRequired =>
      'Angiv server-URL\'en inden bekræftelse.';

  @override
  String get successTitle => 'Succes';

  @override
  String get graphhopperServerReachable => 'GraphHopper-server tilgængelig';

  @override
  String get errorTitle => 'Fejl';

  @override
  String get close => 'Luk';

  @override
  String get infoVersion => 'Version';

  @override
  String get infoProtocol => 'Protokol';

  @override
  String get infoMaps => 'Kort';

  @override
  String get infoRouting => 'Ruting';

  @override
  String get infoSource => 'Kilde';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (selvhostet)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (sky)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Ikke forbundet';

  @override
  String get loginWithNostrTitle => 'LOG IND MED NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Privat nøgle forlader aldrig din enhed · Anbefalet';

  @override
  String get nsecLoginOption => 'Indsæt nsec';

  @override
  String get nsecLoginDescription =>
      'Privat nøgle gemt lokalt · Mindre sikkert';

  @override
  String get connectedViaAmber => 'Forbundet via Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Forbundet via nsec';

  @override
  String get publicKeyLabel => 'OFFENTLIG NØGLE';

  @override
  String get npubCopiedToClipboard => 'npub kopieret til udklipsholder';

  @override
  String get logoutTitle => 'Afbryd forbindelsen';

  @override
  String get logoutConfirmation =>
      'Fjern Nostr-legitimationsoplysninger fra denne enhed?';

  @override
  String get logoutButton => 'Afbryd forbindelsen';

  @override
  String get nostrIdentityInfo =>
      'Med en Nostr-identitet kan du publicere trafikadvarsler, rapporter og interessepunkter på en decentraliseret måde på Nostr-netværket, uden centrale servere.';

  @override
  String get warningTitle => 'Advarsel';

  @override
  String get nsecWarning =>
      'Du er ved at skrive din Nostr-private nøgle direkte ind i en app. Enhver med fysisk eller fjern adgang til din enhed kan læse den og permanent overtage kontrollen over din Nostr-identitet.';

  @override
  String get amberSecureMethodHint =>
      '✦  Den sikre metode er Amber (NIP-55): nsec forlader aldrig app-underskriverens boks.';

  @override
  String get nsecRiskAcknowledgment =>
      'Jeg forstår risikoen og vil alligevel fortsætte';

  @override
  String get continueButton => 'Fortsæt';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber er en NIP-55-kompatibel Android app-underskriver. Din private nøgle forbliver isoleret i Amber og deles aldrig.';

  @override
  String get requestKeyFromAmber => 'Anmod om offentlig nøgle fra Amber';

  @override
  String get amberNotFound =>
      'Amber ikke fundet. Installer det fra Play Store eller indtast din npub manuelt.';

  @override
  String get waitingForAmberResponse => 'Venter på Amber-svar…';

  @override
  String get pasteNpubManually => 'Indsæt din npub manuelt:';

  @override
  String get confirmNpub => 'Bekræft npub';

  @override
  String get enterNsecTitle => 'Indsæt nsec';

  @override
  String get loginButton => 'Log ind';

  @override
  String get invalidNpubTitle => 'Ugyldig npub';

  @override
  String get invalidNsecTitle => 'Ugyldig nsec';

  @override
  String get invalidNpubMessage =>
      'Sørg for, at du indsatte den korrekte npub.';

  @override
  String get invalidNsecMessage =>
      'Sørg for, at du indsatte den korrekte nsec.';

  @override
  String get amberResponseError => 'Amber-svarfejl';

  @override
  String get ok => 'OK';

  @override
  String get or => 'eller';

  @override
  String get gpsNotActiveTitle => 'GPS ikke aktiv';

  @override
  String get gpsDisabledMessage =>
      'Aktiver GPS i dine enhedsindstillinger for at få din placering og bruge navigation.';

  @override
  String get openSettings => 'Indstillinger';

  @override
  String get myLocation => 'Min placering';

  @override
  String get loginToReport =>
      'Log ind med Nostr (Profil-sektion) for at rapportere hændelser';

  @override
  String get navigateHere => 'Naviger hertil';

  @override
  String get reportEventHere => 'Rapporter hændelse her';

  @override
  String get zapSendSats => 'Zap ⚡ (send sats)';

  @override
  String get sendZap => 'Send en Zap';

  @override
  String get chooseAmountSats => 'Vælg beløb i satoshi (sats):';

  @override
  String get customAmount => 'Brugerdefineret beløb…';

  @override
  String get zapSending => 'Sender…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Henter Lightning-adresse…';

  @override
  String get noLightningAddress => 'Denne reporter har ingen Lightning-adresse';

  @override
  String get requestingInvoice => 'Anmoder om faktura…';

  @override
  String get lnurlUnavailable => 'LNURL ikke tilgængelig';

  @override
  String get invoiceFailed => 'Kunne ikke generere faktura';

  @override
  String get openingWallet => 'Åbner pung…';

  @override
  String get payingViaNwc => 'Betaler via NWC…';

  @override
  String get noLightningWallet => 'Ingen Lightning-pung fundet';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats sendt!';
  }

  @override
  String get stillThere => 'Stadig der';

  @override
  String get notThereAnymore => 'Ikke der længere';

  @override
  String get loginToConfirm =>
      'Log ind med Nostr for at bekræfte eller bestride';

  @override
  String get reportAnEvent => 'Rapporter en hændelse';

  @override
  String get optionalComment => 'Valgfri kommentar…';

  @override
  String get publish => 'Publicer';

  @override
  String get publishing => 'Publicerer…';

  @override
  String get reportPublished => 'Rapport publiceret ✓';

  @override
  String get myReports => 'MINE RAPPORTER';

  @override
  String get noReportsYet => 'Ingen rapporter publiceret';

  @override
  String get zapBalance => 'Zap-saldo';

  @override
  String get satoshiFromReports => 'Satoshi modtaget fra dine rapporter';

  @override
  String get reputationHigh => 'Høj';

  @override
  String get reputationMedium => 'Middel';

  @override
  String get reputationLow => 'Lav';

  @override
  String reputationLabel(String level) {
    return 'Omdømme $level';
  }

  @override
  String reliability(int pct) {
    return 'Pålidelighed: $pct%';
  }

  @override
  String get confirmedLabel => 'Bekræftet';

  @override
  String get removedLabel => 'Fjernet';

  @override
  String get positionLabel => 'Position';

  @override
  String get loadingLabel => 'Indlæser…';

  @override
  String get sectionWebSearch => 'Websøgning';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Indsæt din NWC-URI (Alby Hub, Mutiny, Cashu…) for at betale Zaps direkte fra appen.';

  @override
  String get searchEngineQwantDesc =>
      'Europæisk, privatlivsorienteret. Ingen sporing, ingen reklameprofiler. Anbefalet.';

  @override
  String get searchEngineBraveDesc =>
      'Uafhængigt indeks, open-source. Ingen Google- eller Bing-afhængighed. Nul profilering.';

  @override
  String get searchEngineDdgDesc =>
      'Privatlivsfokuseret og populær. Resultater delvist fra Bing — husk det.';

  @override
  String get searchEngineStartpageDesc =>
      'Google-resultater uden at aflevere dine data til Google. Et fornuftigt kompromis.';

  @override
  String get searchEngineGoogleDesc =>
      'Meget effektiv. Men husk: Google kender dig bedre end din mor og sælger alt til annoncører. Dit valg. 🍪';

  @override
  String get categoryPolice => 'Politi';

  @override
  String get categorySpeedCamera => 'Fartkamera';

  @override
  String get categoryTrafficJam => 'Trafikprop';

  @override
  String get categoryAccident => 'Ulykke';

  @override
  String get categoryRoadClosure => 'Vejspærring';

  @override
  String get categoryConstruction => 'Vejarbejde';

  @override
  String get categoryHazard => 'Fare';

  @override
  String get categoryRoadCondition => 'Vejforhold';

  @override
  String get categoryPothole => 'Hul i vejen';

  @override
  String get categoryFog => 'Tåge';

  @override
  String get categoryIce => 'Is';

  @override
  String get categoryAnimal => 'Dyr';

  @override
  String get categoryOther => 'Andet';

  @override
  String get dateTimeLabel => 'Dato / tid';

  @override
  String minutesAgo(int count) {
    return 'for $count min siden';
  }

  @override
  String hoursAgo(int count) {
    return 'for ${count}t siden';
  }

  @override
  String daysAgo(int count) {
    return 'for ${count}d siden';
  }

  @override
  String get sectionFavorites => 'Gemte steder';

  @override
  String get addFavorite => 'Tilføj sted';

  @override
  String get favoriteLabelHint => 'Navn (f.eks. Hjem, Kontor)';

  @override
  String get favoriteAddressHint => 'Adresse';

  @override
  String get favoriteGeocodingError =>
      'Adressen blev ikke fundet. Prøv en mere præcis adresse.';

  @override
  String get trafficAlertTitle => 'Ny trafik på ruten';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category rapporteret $age på din rute.\n\nVil du finde en alternativ rute?';
  }

  @override
  String get trafficContinue => 'Fortsæt';

  @override
  String get trafficRecalculate => 'Genberegn rute';

  @override
  String get navExitTitle => 'Afslut navigation?';

  @override
  String get navExitBody =>
      'Vil du stoppe navigationen og vende tilbage til kortet?';

  @override
  String get navContinue => 'Fortsæt navigation';

  @override
  String get navExit => 'Ja, afslut';

  @override
  String get loadingInfo => 'Indlæser oplysninger…';

  @override
  String get conditionsOnRoute => 'Betingelser på ruten';

  @override
  String get calculateRoute => 'Beregn rute';

  @override
  String get sectionNavigationVoice => 'Navigationsstemme';

  @override
  String get voiceGuidance => 'Stemmevejledning';

  @override
  String get voiceGuidanceDesc =>
      'Læs sving-instruktioner højt under navigation';

  @override
  String get testVoiceEngine => 'Test stemmemotor';

  @override
  String get testVoiceEngineDesc =>
      'Tryk for at tjekke TTS-motoren og få opsætningsinstruktioner';

  @override
  String get ttsDialogTitle => 'Manglende stemmemotor';

  @override
  String get ttsDialogBody =>
      'Der blev ikke fundet nogen fungerende Text-to-Speech-motor.\n\nRoadstr bygger udelukkende på open source-software — installer en af disse gratis motorer fra F-Droid:';

  @override
  String get ttsRhvoiceDesc =>
      'Naturligt klingende stemme, begrænset sprogliste';

  @override
  String get ttsEspeakDesc => 'Dækker over 100 sprog, robotagtig stemme';

  @override
  String get ttsInstallNote =>
      '⚠️ Efter installation:\n1. Android-indstillinger → Hjælpefunktioner → Tekst-til-tale\n2. Vælg den motor, du lige har installeret\n3. Download stemmedata til dit sprog\n4. Genstart Roadstr helt';

  @override
  String get ttsTestNow => 'Test nu';

  @override
  String get voiceUnsupportedTitle => 'Stemmevejledning ikke tilgængelig';

  @override
  String get voiceUnsupportedBody =>
      'Dit sprog understøttes endnu ikke til talte sving-anvisninger. Navigationsinstruktioner vil stadig blive vist som tekst på skærmen.';

  @override
  String get kokoroModelTitle => 'Stemmemodel (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Ikke downloadet · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Downloader...';

  @override
  String get kokoroModelStatusReady => 'Stemmemodel er klar';

  @override
  String get kokoroModelDownloadBtn => 'Download';

  @override
  String get kokoroModelSupportedLangs =>
      'Understøtter: italiensk, engelsk, spansk, fransk, japansk, kinesisk, portugisisk';

  @override
  String get autoDarkMode => 'Auto mørkt tema';

  @override
  String get autoDarkModeDesc => 'Aktiverer det mørke tema ved solnedgang';

  @override
  String get settingsImperialUnits => 'Imperiale enheder';

  @override
  String get settingsImperialUnitsDesc =>
      'Miles og fod i stedet for kilometer og meter';

  @override
  String get arrivedTitle => '🎉 Du er fremme!';

  @override
  String get arrivedBody => 'Du har nået din destination.';

  @override
  String get arrivedFeedbackPrompt => 'Hvordan gik det?';

  @override
  String get feedbackBad => 'Dårligt';

  @override
  String get feedbackGood => 'Godt!';

  @override
  String get feedbackDialogTitle => 'Fortæl os hvad der gik galt';

  @override
  String get feedbackHint => 'Beskriv problemet…';

  @override
  String get feedbackSent => 'Feedback sendt — tak! 🙏';

  @override
  String get feedbackSubmit => 'Send';

  @override
  String get transportModeCar => 'Bil';

  @override
  String get transportModeWalk => 'Til fods';

  @override
  String etaArrivalLabel(String time) {
    return 'Ank. $time';
  }

  @override
  String get supportRoadstr => 'Støt Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address kopieret til udklipsholder';
  }

  @override
  String get disclaimerTitle => 'Vigtig meddelelse';

  @override
  String get disclaimerAccept => 'Jeg har læst og accepterer';

  @override
  String get disclaimerBody =>
      'Roadstr er en eksperimentel, open source-navigationsapp vedligeholdt af fællesskabet, baseret på data fra OpenStreetMap og Nostr-protokollen, og stillet til rådighed til brug i ethvert land. Ved at downloade, installere eller bruge denne app accepterer brugeren betingelsesløst alle nedenstående vilkår, uden territorial begrænsning.\n\n🚗 TRAFIKSIKKERHED FØRST\nFøreren skal altid holde øjnene på vejen og overholde alle gældende færdselsregler og opsatte skilte, som altid har forrang frem for enhver instruktion fra appen. Betjen aldrig enheden under kørsel; fastgør den i en godkendt, synlig holder, inden du kører af sted, og lad dig aldrig distrahere fra vejen for at interagere med den, mens køretøjet er i bevægelse.\n\n⚠️ PÅTAGELSE AF RISIKO — VERDENSOMSPÆNDENDE\nVed at bruge Roadstr, i ethvert land og under ethvert retssystem, påtager brugeren sig bevidst og frivilligt ALLE risici forbundet med brugen heraf, herunder, men ikke begrænset til: trafikulykker, personskade, dødsfald, tingsskade, køretøjsskade, bøder, administrative sanktioner, bugsering, beslaglæggelse, strafferetligt ansvar eller enhver anden konsekvens, der direkte eller indirekte opstår som følge af tillid til appen. Brugeren bærer alene det fulde ansvar for enhver kørsels- og navigationsbeslutning.\n\n🚫 INGEN GARANTI\nRoadstr leveres udelukkende „SOM DEN ER“ og „SOM TILGÆNGELIG“, uden nogen form for garanti, hverken udtrykkelig, underforstået eller lovbestemt — herunder, uden begrænsning, garantier for nøjagtighed, fuldstændighed, pålidelighed, tilgængelighed, salgbarhed, egnethed til et bestemt formål og ikke-krænkelse. Kortdata, ruteplanlægning, hastighedsgrænser, fartkameraer og oplysninger om zoner med kørselsbegrænsning (ZTL/ZAC/LTZ) kommer fra åbne, fællesskabsvedligeholdte kilder (OpenStreetMap, Overpass API), som til enhver tid og uden varsel kan være ufuldstændige, forældede eller unøjagtige for ethvert land, enhver region eller kommune. Brugeren er alene ansvarlig for selvstændigt at kontrollere, før og under rejsen, lovligheden og fremkommeligheden af enhver foreslået rute i forhold til officiel lokal skiltning og lovgivning.\n\n📍 NØJAGTIGHED OG GPS\nGPS-positionering kan være unøjagtig eller utilgængelig. Alle anvisninger, afstande og advarsler gives udelukkende som vejledning og må aldrig anvendes som eneste grundlag for en køremæssig beslutning.\n\n🛡️ ANSVARSBEGRÆNSNING\nI det videst mulige omfang tilladt efter gældende lov i enhver jurisdiktion er udviklerne, bidragyderne og enhver part involveret i at skabe eller distribuere Roadstr ikke ansvarlige for direkte, indirekte, hændelige, følgeskader, særlige, eksemplariske eller pønalt begrundede skader af nogen art — herunder personskade, dødsfald eller økonomisk tab — der opstår som følge af eller i forbindelse med brugen eller manglende evne til at bruge appen, selv hvis der er givet meddelelse om muligheden for sådanne skader. Hvis en given jurisdiktion ikke tillader nogen eller alle af disse begrænsninger, begrænses ansvaret i det mindste omfang, der er tilladt efter loven i den pågældende jurisdiktion.\n\n🤝 SKADESLØSHOLDELSE\nBrugeren accepterer at skadesløsholde og friholde udviklerne og bidragyderne fra ethvert krav, enhver skade, ethvert tab eller enhver udgift (herunder advokatsalærer), der opstår som følge af brugerens brug af appen eller overtrædelse af disse vilkår.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ DELVIS UGYLDIGHED\nHvis en bestemmelse i disse vilkår findes uden retskraft i en given jurisdiktion, skal denne bestemmelse begrænses eller udskilles i det mindste nødvendige omfang, og alle øvrige bestemmelser skal forblive i fuld kraft og virkning.\n\nVed at bruge Roadstr, uanset hvor i verden, bekræfter brugeren at have læst, forstået og betingelsesløst accepteret hvert af ovenstående vilkår og påtager sig det fulde og hele ansvar — og al risiko — for brugen af appen og enhver konsekvens, der opstår heraf.';

  @override
  String get readOnWikipedia => 'Læs på Wikipedia';

  @override
  String get openInBrowser => 'Åbn i browser';

  @override
  String get wikipediaLoadFailed => 'Wikipedia kunne ikke indlæses sikkert.';

  @override
  String get retry => 'Prøv igen';

  @override
  String get poiDetailsFromOsm => 'Oplysninger fra OpenStreetMap';

  @override
  String get poiCategory => 'Kategori';

  @override
  String get poiOperator => 'Operatør';

  @override
  String get poiCuisine => 'Køkken';

  @override
  String get poiAccessibility => 'Tilgængelighed';

  @override
  String get poiWheelchairYes => 'Kørestolstilgængelig';

  @override
  String get poiWheelchairLimited => 'Begrænset adgang for kørestole';

  @override
  String get poiWheelchairNo => 'Ikke tilgængelig for kørestole';

  @override
  String get poiContact => 'Kontakt';

  @override
  String get poiAddress => 'Adresse';

  @override
  String get poiWebsite => 'Websted';

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
  String get gpsSignalLost => 'GPS-signal mistet';

  @override
  String get poiOpenNow => 'Åbent nu';

  @override
  String get poiClosedNow => 'Lukket';

  @override
  String poiOpensAt(String when) {
    return 'Åbner: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Lukker: $when';
  }

  @override
  String searchOnEngine(String engine) {
    return 'Søg på $engine';
  }

  @override
  String get plannerFromHint => 'Fra…';

  @override
  String get plannerToHint => 'Destination…';

  @override
  String departEta(String dep, String arr) {
    return 'Afgang $dep  →  Ankomst $arr';
  }

  @override
  String get modeCar => 'Bil';

  @override
  String get modeBike => 'Cykel';

  @override
  String get modeWalk => 'Til fods';

  @override
  String windSpeed(String speed) {
    return 'vind $speed km/h';
  }

  @override
  String durationMin(int m) {
    return '$m min';
  }

  @override
  String durationHourMin(int h, int m) {
    return '${h}t ${m}min';
  }

  @override
  String get weatherClear => 'Klart';

  @override
  String get weatherPartlyCloudy => 'Delvis skyet';

  @override
  String get weatherCloudy => 'Skyet';

  @override
  String get weatherFog => 'Tåge';

  @override
  String get weatherLightRain => 'Let regn';

  @override
  String get weatherRain => 'Regn';

  @override
  String get weatherSnow => 'Sne';

  @override
  String get weatherShowers => 'Byger';

  @override
  String get weatherThunderstorm => 'Tordenvejr';

  @override
  String get ztlAheadWarning =>
      'Begrænset trafikzone forude — ruten fører igennem';

  @override
  String get ztlInsideWarning => 'Begrænset trafikzone';

  @override
  String get onboardingAppSubtitle => 'Open source Nostr-navigation';

  @override
  String get onboardingWelcomeTitle => 'Velkommen';

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
  String get onboardingGetStarted => 'Kom i gang';

  @override
  String get onboardingNostrTitle => 'Nostr-identitet';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Forbundet';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (anbefalet)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec-nøgle';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Ugyldig nsec-nøgle — kontroller og prøv igen.';

  @override
  String get onboardingSkip => 'Spring over for nu';

  @override
  String get onboardingContinue => 'Fortsæt';

  @override
  String get onboardingEnterNsec => 'Indtast nsec-nøgle';

  @override
  String get onboardingSetupTitle => 'Konfigurer Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Placering';

  @override
  String get onboardingLocationGranted => 'Placeringsadgang givet';

  @override
  String get onboardingLocationRequired => 'Kræves til GPS-navigation';

  @override
  String get onboardingGrantButton => 'Giv adgang';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS-brugere';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

  @override
  String get onboardingVoiceTitle => 'AI-stemme guidance (valgfrit)';

  @override
  String get onboardingVoiceReady => 'Kokoro stemmemodel klar';

  @override
  String get onboardingVoiceDownloading => 'Downloader stemmemodel…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Download';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Du er klar til at navigere!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Kør!';

  @override
  String get onboardingProfileLoading => 'Indlæser profil…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Stemme';

  @override
  String get kokoroVoiceFemale => 'Kvindelig';

  @override
  String get kokoroVoiceMale => 'Mandlig';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Mandlig stemme er ikke tilgængelig for dette sprog';

  @override
  String get kokoroSpeedTitle => 'Talehastighed';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Valgfrit: dine gemte favoritter kan synkroniseres mellem dine enheder via Nostr-relæerne, end-to-end-krypteret (NIP-44) med din egen nøgle — relæerne ser kun krypteret tekst, og ingen andre end dig kan læse indholdet. Aktivér når som helst i Indstillinger.';

  @override
  String get parkingSaveHere => 'Gem parkering her';

  @override
  String get parkingMarkerTitle => 'Parkeringsplads';

  @override
  String get parkingNavigateHere => 'Navigér til parkering';

  @override
  String get parkingRemove => 'Fjern parkering';

  @override
  String get parkingSavedSnack => 'Parkeringsplads gemt';

  @override
  String get parkingRemovedSnack => 'Parkeringsplads fjernet';

  @override
  String get exportFavoritesTitle => 'Eksportér favoritter';

  @override
  String get exportFavoritesDesc =>
      'Gem dine favoritsteder i en JSON-fil, som du kan sikkerhedskopiere eller flytte til en anden enhed.';

  @override
  String get exportEncryptToggle => 'Kryptér med adgangskode';

  @override
  String get exportPasswordHint => 'Adgangskode';

  @override
  String get exportButton => 'Eksportér';

  @override
  String get exportSuccessSnack => 'Favoritter eksporteret';

  @override
  String get exportFailedSnack => 'Eksport mislykkedes';

  @override
  String get importFavoritesTitle => 'Importér favoritter';

  @override
  String get importPasswordPrompt =>
      'Denne fil er krypteret — indtast adgangskoden';

  @override
  String importSuccessSnack(int n) {
    return '$n favoritter importeret';
  }

  @override
  String get importFailedSnack =>
      'Import mislykkedes — tjek filen og adgangskoden';

  @override
  String get syncFavoritesTitle => 'Synkronisér favoritter';

  @override
  String get syncFavoritesDesc =>
      'Udgiv dine favoritter, end-to-end-krypteret, til dine Nostr-relæer, så de følger dig på tværs af enheder. Relæerne ser kun krypteret tekst — ingen andre end dig kan læse indholdet.';

  @override
  String get syncNowButton => 'Send til Nostr';

  @override
  String get syncPullButton => 'Hent fra Nostr';

  @override
  String get syncPassphraseTitle => 'Synkroniserings-adgangssætning (valgfri)';

  @override
  String get syncPassphraseDesc =>
      'Andet krypteringslag for synkroniserede favoritter: selv hvis din Nostr-nøgle blev kompromitteret, forbliver øjebliksbilledet på relæerne ulæseligt uden denne adgangssætning. Du indtaster den én gang på hver ny enhed. Lad feltet stå tomt for at deaktivere.';

  @override
  String get syncSuccessSnack => 'Favoritter synkroniseret';

  @override
  String get syncFailedSnack => 'Synkronisering mislykkedes';

  @override
  String syncLastSyncLabel(String when) {
    return 'Sidst synkroniseret: $when';
  }

  @override
  String get syncNoIdentity =>
      'Log ind med Nostr (Indstillinger → Profil) for at aktivere synkronisering';

  @override
  String get onboardingVpnNotice =>
      'For maksimal privatliv — rapporter spredes via Nostr-netværket — anbefales det at bruge en VPN. Mullvad, som kan betales med Bitcoin, er det anbefalede valg.';

  @override
  String get trafficNormal => 'Normal trafik';

  @override
  String get trafficModerate => 'Moderat trafik';

  @override
  String get trafficHeavy => 'Tæt trafik';

  @override
  String get avoidHighwaysChip => 'Undgå motorveje';

  @override
  String get avoidTollsChip => 'Undgå betalingsveje';

  @override
  String get preferShorterChip => 'Korteste rute';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Vis ruteforhåndsvisning';
}
