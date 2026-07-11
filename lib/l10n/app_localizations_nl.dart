// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Dutch Flemish (`nl`).
class AppLocalizationsNl extends AppLocalizations {
  AppLocalizationsNl([String locale = 'nl']) : super(locale);

  @override
  String get searchHint => 'Waar wilt u naartoe?';

  @override
  String get calculatingRoute => 'Route berekenen…';

  @override
  String get routeNotFoundTitle => 'Route niet gevonden';

  @override
  String get noRouteFound => 'Geen route gevonden. Controleer uw verbinding.';

  @override
  String get emptyServerResponse =>
      'Leeg serverantwoord. Controleer uw verbinding.';

  @override
  String routeError(String error) {
    return 'Fout bij routeberekening: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS niet beschikbaar — Instellingen → App → Roadstr → Machtigingen → Locatie';

  @override
  String get acquiringGps => 'GPS verkrijgen…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopper-server niet geconfigureerd — OSRM wordt gebruikt';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper API-sleutel niet geconfigureerd — OSRM wordt gebruikt';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService API-sleutel niet geconfigureerd — OSRM wordt gebruikt';

  @override
  String get chooseRoute => 'Route kiezen';

  @override
  String routeOptionsCount(int count) {
    return '$count opties';
  }

  @override
  String get cancel => 'Annuleren';

  @override
  String get startNavigation => 'Navigatie starten';

  @override
  String get fastestRoute => 'Snelste';

  @override
  String get now => 'Nu';

  @override
  String get history => 'Geschiedenis';

  @override
  String get clearHistory => 'Wissen';

  @override
  String get selectedPosition => 'Geselecteerde positie';

  @override
  String get bottomBarProfile => 'Profiel';

  @override
  String get bottomBarMenu => 'Menu';

  @override
  String get settingsTitle => 'Instellingen';

  @override
  String get sectionTheme => 'Thema';

  @override
  String get sectionMap => 'Kaart';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Taal';

  @override
  String get themeLightNostr => 'Licht · Nostr Violet';

  @override
  String get themeLightBitcoin => 'Licht · Bitcoin Oranje';

  @override
  String get themeDarkNostr => 'Donker · Nostr Violet';

  @override
  String get themeDarkBitcoin => 'Donker · Bitcoin Oranje';

  @override
  String get langSystem => 'Systeemstandaard';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Scherm ingeschakeld houden';

  @override
  String get keepScreenOnDescription => 'Voorkomt slaapstand tijdens navigatie';

  @override
  String get rotateMap => 'Kaart volgt richting';

  @override
  String get rotateMapDescription => 'Kaart draait mee met rijrichting';

  @override
  String get mapTileUrlLabel => 'Kaart-tegel URL';

  @override
  String get routingProviderLabel => 'Routingprovider';

  @override
  String get osrmProvider => 'OSRM (openbaar, geen sleutel vereist)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokaal/privé)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API-sleutel)';

  @override
  String get openrouteProvider => 'OpenRouteService (API-sleutel)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API-sleutel (optioneel)';

  @override
  String get verify => 'Verifiëren';

  @override
  String get graphhopperServerUrlRequired =>
      'Voer de server-URL in voor verificatie.';

  @override
  String get successTitle => 'Geslaagd';

  @override
  String get graphhopperServerReachable => 'GraphHopper-server bereikbaar';

  @override
  String get errorTitle => 'Fout';

  @override
  String get close => 'Sluiten';

  @override
  String get infoVersion => 'Versie';

  @override
  String get infoProtocol => 'Protocol';

  @override
  String get infoMaps => 'Kaarten';

  @override
  String get infoRouting => 'Routering';

  @override
  String get infoSource => 'Bron';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (zelf gehost)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profiel';

  @override
  String get notConnected => 'Niet verbonden';

  @override
  String get loginWithNostrTitle => 'INLOGGEN MET NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Privésleutel verlaat nooit uw apparaat · Aanbevolen';

  @override
  String get nsecLoginOption => 'nsec invoeren';

  @override
  String get nsecLoginDescription =>
      'Privésleutel lokaal opgeslagen · Minder veilig';

  @override
  String get connectedViaAmber => 'Verbonden via Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Verbonden via nsec';

  @override
  String get publicKeyLabel => 'PUBLIEKE SLEUTEL';

  @override
  String get npubCopiedToClipboard => 'npub naar klembord gekopieerd';

  @override
  String get logoutTitle => 'Verbinding verbreken';

  @override
  String get logoutConfirmation =>
      'Nostr-inloggegevens van dit apparaat verwijderen?';

  @override
  String get logoutButton => 'Verbinding verbreken';

  @override
  String get nostrIdentityInfo =>
      'Met een Nostr-identiteit kunt u verkeersmeldingen, rapporten en bezienswaardigheden gedecentraliseerd publiceren op het Nostr-netwerk, zonder centrale servers.';

  @override
  String get warningTitle => 'Waarschuwing';

  @override
  String get nsecWarning =>
      'U staat op het punt uw Nostr-privésleutel rechtstreeks in een app in te voeren. Iedereen met fysieke of externe toegang tot uw apparaat kan deze lezen en permanent de controle over uw Nostr-identiteit overnemen.';

  @override
  String get amberSecureMethodHint =>
      '✦  De veilige methode is Amber (NIP-55): de nsec verlaat nooit de app-ondertekenaarkluis.';

  @override
  String get nsecRiskAcknowledgment =>
      'Ik begrijp het risico en wil toch doorgaan';

  @override
  String get continueButton => 'Doorgaan';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber is een NIP-55-compatibele Android app-ondertekenaar. Uw privésleutel blijft geïsoleerd in Amber en wordt nooit gedeeld.';

  @override
  String get requestKeyFromAmber => 'Publieke sleutel aanvragen bij Amber';

  @override
  String get amberNotFound =>
      'Amber niet gevonden. Installeer het via de Play Store of voer uw npub handmatig in.';

  @override
  String get waitingForAmberResponse => 'Wachten op Amber-reactie…';

  @override
  String get pasteNpubManually => 'Plak uw npub handmatig:';

  @override
  String get confirmNpub => 'npub bevestigen';

  @override
  String get enterNsecTitle => 'nsec invoeren';

  @override
  String get loginButton => 'Inloggen';

  @override
  String get invalidNpubTitle => 'Ongeldige npub';

  @override
  String get invalidNsecTitle => 'Ongeldige nsec';

  @override
  String get invalidNpubMessage =>
      'Controleer of u de juiste npub hebt geplakt.';

  @override
  String get invalidNsecMessage =>
      'Controleer of u de juiste nsec hebt geplakt.';

  @override
  String get amberResponseError => 'Amber-antwoordfout';

  @override
  String get ok => 'OK';

  @override
  String get or => 'of';

  @override
  String get gpsNotActiveTitle => 'GPS niet actief';

  @override
  String get gpsDisabledMessage =>
      'Schakel GPS in via uw apparaatinstellingen om uw locatie te bepalen en navigatie te gebruiken.';

  @override
  String get openSettings => 'Instellingen';

  @override
  String get myLocation => 'Mijn locatie';

  @override
  String get loginToReport =>
      'Log in met Nostr (sectie Profiel) om gebeurtenissen te melden';

  @override
  String get navigateHere => 'Hier naartoe navigeren';

  @override
  String get reportEventHere => 'Gebeurtenis hier melden';

  @override
  String get zapSendSats => 'Zap ⚡ (sats sturen)';

  @override
  String get sendZap => 'Een Zap sturen';

  @override
  String get chooseAmountSats => 'Kies bedrag in satoshi (sats):';

  @override
  String get customAmount => 'Aangepast bedrag…';

  @override
  String get zapSending => 'Versturen…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Lightning-adres ophalen…';

  @override
  String get noLightningAddress => 'Deze melder heeft geen Lightning-adres';

  @override
  String get requestingInvoice => 'Factuur aanvragen…';

  @override
  String get lnurlUnavailable => 'LNURL niet beschikbaar';

  @override
  String get invoiceFailed => 'Kan factuur niet genereren';

  @override
  String get openingWallet => 'Portemonnee openen…';

  @override
  String get payingViaNwc => 'Betalen via NWC…';

  @override
  String get noLightningWallet => 'Geen Lightning-portemonnee gevonden';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats verzonden!';
  }

  @override
  String get stillThere => 'Nog aanwezig';

  @override
  String get notThereAnymore => 'Niet meer aanwezig';

  @override
  String get loginToConfirm =>
      'Log in met Nostr om te bevestigen of te betwisten';

  @override
  String get reportAnEvent => 'Een gebeurtenis melden';

  @override
  String get optionalComment => 'Optionele opmerking…';

  @override
  String get publish => 'Publiceren';

  @override
  String get publishing => 'Publiceren…';

  @override
  String get reportPublished => 'Rapport gepubliceerd ✓';

  @override
  String get myReports => 'MIJN RAPPORTEN';

  @override
  String get noReportsYet => 'Geen rapporten gepubliceerd';

  @override
  String get zapBalance => 'Zap-saldo';

  @override
  String get satoshiFromReports => 'Ontvangen satoshi uit uw rapporten';

  @override
  String get reputationHigh => 'Hoog';

  @override
  String get reputationMedium => 'Gemiddeld';

  @override
  String get reputationLow => 'Laag';

  @override
  String reputationLabel(String level) {
    return 'Reputatie $level';
  }

  @override
  String reliability(int pct) {
    return 'Betrouwbaarheid: $pct%';
  }

  @override
  String get confirmedLabel => 'Bevestigd';

  @override
  String get removedLabel => 'Verwijderd';

  @override
  String get positionLabel => 'Positie';

  @override
  String get loadingLabel => 'Laden…';

  @override
  String get sectionWebSearch => 'Webzoeken';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Plak uw NWC-URI (Alby Hub, Mutiny, Cashu…) om Zaps rechtstreeks vanuit de app te betalen.';

  @override
  String get searchEngineQwantDesc =>
      'Europees, privacy-first. Geen tracking, geen advertentieprofielen. Aanbevolen.';

  @override
  String get searchEngineBraveDesc =>
      'Onafhankelijke index, open-source. Geen Google- of Bing-afhankelijkheid. Nul profilering.';

  @override
  String get searchEngineDdgDesc =>
      'Privacygericht en populair. Resultaten gedeeltelijk van Bing — houd dat in gedachten.';

  @override
  String get searchEngineStartpageDesc =>
      'Google-resultaten zonder uw gegevens aan Google te geven. Een redelijk compromis.';

  @override
  String get searchEngineGoogleDesc =>
      'Zeer effectief. Maar onthoud: Google kent u beter dan uw moeder en verkoopt alles aan adverteerders. Uw keuze. 🍪';

  @override
  String get categoryPolice => 'Politie';

  @override
  String get categorySpeedCamera => 'Flitser';

  @override
  String get categoryTrafficJam => 'File';

  @override
  String get categoryAccident => 'Ongeluk';

  @override
  String get categoryRoadClosure => 'Wegafsluiting';

  @override
  String get categoryConstruction => 'Wegwerkzaamheden';

  @override
  String get categoryHazard => 'Gevaar';

  @override
  String get categoryRoadCondition => 'Wegconditie';

  @override
  String get categoryPothole => 'Kuil';

  @override
  String get categoryFog => 'Mist';

  @override
  String get categoryIce => 'IJzel';

  @override
  String get categoryAnimal => 'Dier';

  @override
  String get categoryOther => 'Overig';

  @override
  String get dateTimeLabel => 'Datum / tijd';

  @override
  String minutesAgo(int count) {
    return '$count min geleden';
  }

  @override
  String hoursAgo(int count) {
    return '${count}u geleden';
  }

  @override
  String daysAgo(int count) {
    return '${count}d geleden';
  }

  @override
  String get sectionFavorites => 'Opgeslagen plaatsen';

  @override
  String get addFavorite => 'Plaats toevoegen';

  @override
  String get favoriteLabelHint => 'Naam (bijv. Thuis, Kantoor)';

  @override
  String get favoriteAddressHint => 'Adres';

  @override
  String get favoriteGeocodingError =>
      'Adres niet gevonden. Probeer een specifieker adres.';

  @override
  String get trafficAlertTitle => 'Nieuw verkeer op route';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category gemeld $age op uw route.\n\nWilt u een alternatieve route vinden?';
  }

  @override
  String get trafficContinue => 'Doorgaan';

  @override
  String get trafficRecalculate => 'Route herberekenen';

  @override
  String get navExitTitle => 'Navigatie beëindigen?';

  @override
  String get navExitBody =>
      'Wilt u de navigatie stoppen en terugkeren naar de kaart?';

  @override
  String get navContinue => 'Navigatie voortzetten';

  @override
  String get navExit => 'Ja, stoppen';

  @override
  String get loadingInfo => 'Informatie laden…';

  @override
  String get conditionsOnRoute => 'Omstandigheden op route';

  @override
  String get calculateRoute => 'Route berekenen';

  @override
  String get sectionNavigationVoice => 'Navigatiestem';

  @override
  String get voiceGuidance => 'Spraakbegeleiding';

  @override
  String get voiceGuidanceDesc =>
      'Afslaginstructies hardop voorlezen tijdens navigatie';

  @override
  String get testVoiceEngine => 'Spraakengine testen';

  @override
  String get testVoiceEngineDesc =>
      'Tik om de TTS-engine te controleren en installatie-instructies te krijgen';

  @override
  String get ttsDialogTitle => 'Spraakengine ontbreekt';

  @override
  String get ttsDialogBody =>
      'Er is geen werkende Text-to-Speech-engine gevonden.\n\nRoadstr vertrouwt uitsluitend op opensourcesoftware — installeer een van deze gratis engines via F-Droid:';

  @override
  String get ttsRhvoiceDesc => 'Natuurlijk klinkende stem, beperkte talenlijst';

  @override
  String get ttsEspeakDesc =>
      'Ondersteunt meer dan 100 talen, robotachtige stem';

  @override
  String get ttsInstallNote =>
      '⚠️ Na installatie:\n1. Android-instellingen → Toegankelijkheid → Tekst-naar-spraak\n2. Selecteer de zojuist geïnstalleerde engine\n3. Download de spraakgegevens voor jouw taal\n4. Start Roadstr volledig opnieuw op';

  @override
  String get ttsTestNow => 'Nu testen';

  @override
  String get voiceUnsupportedTitle => 'Spraakbegeleiding niet beschikbaar';

  @override
  String get voiceUnsupportedBody =>
      'Je taal wordt nog niet ondersteund voor gesproken aanwijzingen. Navigatie-instructies blijven wel als tekst op het scherm verschijnen.';

  @override
  String get kokoroModelTitle => 'Stemmodel (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Niet gedownload · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Downloaden...';

  @override
  String get kokoroModelStatusReady => 'Stemmodel klaar';

  @override
  String get kokoroModelDownloadBtn => 'Downloaden';

  @override
  String get kokoroModelSupportedLangs =>
      'Ondersteunt: Italiaans, Engels, Spaans, Frans, Japans, Chinees, Portugees';

  @override
  String get autoDarkMode => 'Automatisch donker thema';

  @override
  String get autoDarkModeDesc =>
      'Activeert het donkere thema bij zonsondergang';

  @override
  String get settingsImperialUnits => 'Imperiale eenheden';

  @override
  String get settingsImperialUnitsDesc =>
      'Mijlen en voet in plaats van kilometers en meters';

  @override
  String get arrivedTitle => '🎉 Je bent er!';

  @override
  String get arrivedBody => 'Je hebt je bestemming bereikt.';

  @override
  String get arrivedFeedbackPrompt => 'Hoe is het gegaan?';

  @override
  String get feedbackBad => 'Slecht';

  @override
  String get feedbackGood => 'Goed!';

  @override
  String get feedbackDialogTitle => 'Vertel ons wat er mis ging';

  @override
  String get feedbackHint => 'Beschrijf het probleem…';

  @override
  String get feedbackSent => 'Feedback verstuurd — bedankt! 🙏';

  @override
  String get feedbackSubmit => 'Versturen';

  @override
  String get transportModeCar => 'Auto';

  @override
  String get transportModeWalk => 'Te voet';

  @override
  String etaArrivalLabel(String time) {
    return 'Aanks. $time';
  }

  @override
  String get supportRoadstr => 'Steun Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address naar klembord gekopieerd';
  }

  @override
  String get disclaimerTitle => 'Belangrijke mededeling';

  @override
  String get disclaimerAccept => 'Ik heb gelezen en accepteer';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Lees op Wikipedia';

  @override
  String searchOnEngine(String engine) {
    return 'Zoeken op $engine';
  }

  @override
  String get plannerFromHint => 'Van…';

  @override
  String get plannerToHint => 'Bestemming…';

  @override
  String departEta(String dep, String arr) {
    return 'Vertrek $dep  →  Aankomst $arr';
  }

  @override
  String get modeCar => 'Auto';

  @override
  String get modeBike => 'Fiets';

  @override
  String get modeWalk => 'Te voet';

  @override
  String windSpeed(String speed) {
    return 'wind $speed km/u';
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
  String get weatherClear => 'Helder';

  @override
  String get weatherPartlyCloudy => 'Gedeeltelijk bewolkt';

  @override
  String get weatherCloudy => 'Bewolkt';

  @override
  String get weatherFog => 'Mist';

  @override
  String get weatherLightRain => 'Lichte regen';

  @override
  String get weatherRain => 'Regen';

  @override
  String get weatherSnow => 'Sneeuw';

  @override
  String get weatherShowers => 'Buien';

  @override
  String get weatherThunderstorm => 'Onweer';

  @override
  String get ztlAheadWarning =>
      'ZTL-zone nadert — route voert door beperkt gebied';

  @override
  String get ztlInsideWarning => 'Verkeersbeperkte zone';

  @override
  String get onboardingAppSubtitle => 'Open-source Nostr-navigatie';

  @override
  String get onboardingWelcomeTitle => 'Welkom';

  @override
  String get onboardingWelcomeBody =>
      'Alles wat een navigatie-app nodig heeft — zonder uw privacy op te geven.';

  @override
  String get onboardingFeatureNav => 'Bochtsgewijze GPS-navigatie';

  @override
  String get onboardingFeatureNostr =>
      'Nostr weggebeurtenissen (flitsers, gevaren, verkeer)';

  @override
  String get onboardingFeatureLightning =>
      'Lightning Network fooien voor eventverslaggevers';

  @override
  String get onboardingFeatureVoice =>
      'AI-stemgeleiding op het apparaat (Kokoro-82M)';

  @override
  String get onboardingFeaturePrivacy =>
      'Geen account vereist — geen tracking, nooit';

  @override
  String get onboardingGetStarted => 'Aan de slag';

  @override
  String get onboardingNostrTitle => 'Nostr-identiteit';

  @override
  String get onboardingNostrSubtitle =>
      'Optioneel — verbinding maken om weggebeurtenissen te melden, waarschuwingen te bevestigen en Lightning fooien te verdienen.';

  @override
  String get onboardingNostrConnected => 'Verbonden';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (aanbevolen)';

  @override
  String get onboardingAmberSubtitle =>
      'Verbinding met de Amber signer-app. Uw privésleutel verlaat Amber nooit.';

  @override
  String get onboardingNsecTitle => 'nsec-sleutel';

  @override
  String get onboardingNsecSubtitle =>
      'Plak uw privésleutel. Opgeslagen in de Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Ongeldige nsec-sleutel — controleer en probeer opnieuw.';

  @override
  String get onboardingSkip => 'Nu overslaan';

  @override
  String get onboardingContinue => 'Doorgaan';

  @override
  String get onboardingEnterNsec => 'Voer nsec-sleutel in';

  @override
  String get onboardingSetupTitle => 'Roadstr instellen';

  @override
  String get onboardingSetupSubtitle =>
      'Configureer locatietoegang en optionele stemgeleiding.';

  @override
  String get onboardingLocationTitle => 'Locatie';

  @override
  String get onboardingLocationGranted => 'Locatietoegang verleend';

  @override
  String get onboardingLocationRequired => 'Vereist voor GPS-navigatie';

  @override
  String get onboardingGrantButton => 'Toestaan';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS-gebruikers';

  @override
  String get onboardingGrapheneBody =>
      'Verleen Exacte locatie (niet Geschatte) en sta toegang Altijd toe (niet alleen tijdens gebruik) in:\nInstellingen → Apps → Roadstr → Machtigingen → Locatie\n\nMet alleen geschatte locatie of \'alleen tijdens gebruik\' verliest GPS-navigatie de positie op de achtergrond.';

  @override
  String get onboardingVoiceTitle => 'AI-stemgeleiding (optioneel)';

  @override
  String get onboardingVoiceReady => 'Kokoro spraakmodel klaar';

  @override
  String get onboardingVoiceDownloading => 'Spraakmodel downloaden…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download het 82 MB Kokoro AI-model voor stem op het apparaat';

  @override
  String get onboardingVoiceChecking => 'Modelstatus controleren…';

  @override
  String get onboardingDownloadButton => 'Downloaden';

  @override
  String get onboardingVoiceLaterHint =>
      'U kunt het spraakmodel ook later downloaden via\nInstellingen → Navigatiestem.';

  @override
  String get onboardingReadyTitle => 'U bent klaar om te navigeren!';

  @override
  String get onboardingReadyBody =>
      'Roadstr opent nu de kaart.\nU kunt alles overige configureren in Instellingen.';

  @override
  String get onboardingLetsGo => 'Laten we gaan!';

  @override
  String get onboardingProfileLoading => 'Profiel laden…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get onboardingVpnNotice =>
      'Voor maximale privacy — meldingen worden via het Nostr-netwerk verspreid — wordt het gebruik van een VPN aanbevolen. Mullvad, te betalen met Bitcoin, is de aanbevolen keuze.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Stel voor een betrouwbare werking de locatietoestemming van de app in op \"Altijd toestaan\", niet alleen tijdens gebruik.';
}
