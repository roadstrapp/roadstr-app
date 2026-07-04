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
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Læs på Wikipedia';

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
}
