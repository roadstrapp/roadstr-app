// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Swedish (`sv`).
class AppLocalizationsSv extends AppLocalizations {
  AppLocalizationsSv([String locale = 'sv']) : super(locale);

  @override
  String get searchHint => 'Vart vill du åka?';

  @override
  String get calculatingRoute => 'Beräknar rutt…';

  @override
  String get routeNotFoundTitle => 'Rutt hittades inte';

  @override
  String get noRouteFound => 'Ingen rutt hittades. Kontrollera din anslutning.';

  @override
  String get acquiringGps => 'Hämtar GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopper-server ej konfigurerad — använder OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper API-nyckel ej konfigurerad — använder OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService API-nyckel ej konfigurerad — använder OSRM';

  @override
  String get chooseRoute => 'Välj rutt';

  @override
  String get cancel => 'Avbryt';

  @override
  String get startNavigation => 'Starta navigering';

  @override
  String get fastestRoute => 'Snabbaste';

  @override
  String get now => 'Nu';

  @override
  String get history => 'Historik';

  @override
  String get clearHistory => 'Rensa';

  @override
  String get selectedPosition => 'Vald position';

  @override
  String get bottomBarNotifications => 'Aviseringar';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Meny';

  @override
  String get notificationsTitle => 'Aviseringar';

  @override
  String get notificationsEmpty => 'Inga aviseringar ännu';

  @override
  String get notificationsEmptyBody =>
      'Zaps och reaktioner på dina trafikrapporter visas här.';

  @override
  String get notificationsLoginRequired => 'Anslut din Nostr-profil';

  @override
  String get notificationsLoginRequiredBody =>
      'Logga in med Amber eller nsec för att få aviseringar från andra användare.';

  @override
  String get settingsTitle => 'Inställningar';

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
  String get cursorVehicleLabel => 'Fordon';

  @override
  String get cursorColorLabel => 'Färg';

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
  String get cursorClassic500 => 'Klassisk Fiat 500';

  @override
  String get cursorColorViolet => 'Violett';

  @override
  String get cursorColorIndigo => 'Indigo';

  @override
  String get cursorColorBlue => 'Blå';

  @override
  String get cursorColorGreen => 'Grön';

  @override
  String get cursorColorYellow => 'Gul';

  @override
  String get cursorColorOrange => 'Orange';

  @override
  String get cursorColorRed => 'Röd';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Språk';

  @override
  String get themeLightNostr => 'Ljust · Nostr Violett';

  @override
  String get themeLightBitcoin => 'Ljust · Bitcoin Orange';

  @override
  String get themeDarkNostr => 'Mörkt · Nostr Violett';

  @override
  String get themeDarkBitcoin => 'Mörkt · Bitcoin Orange';

  @override
  String get langSystem => 'Systemstandard';

  @override
  String get keepScreenOn => 'Håll skärmen på';

  @override
  String get keepScreenOnDescription => 'Förhindrar viloläge under navigering';

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
  String get showAltitude => 'Visa höjd';

  @override
  String get showAltitudeDescription =>
      'En liten höjdvisning i realtid nära kartkontrollerna';

  @override
  String get autoCenterOnLaunch => 'Centrera på min position vid start';

  @override
  String get autoCenterOnLaunchDesc =>
      'Använder GPS automatiskt endast om platsbehörighet redan har beviljats';

  @override
  String get mapTileUrlLabel => 'URL för kartbrickor';

  @override
  String get routingProviderLabel => 'Ruttleverantör';

  @override
  String get osrmProvider => 'OSRM (offentlig, ingen nyckel krävs)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokal/privat)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API-nyckel)';

  @override
  String get openrouteProvider => 'OpenRouteService (API-nyckel)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API-nyckel (valfritt)';

  @override
  String get verify => 'Verifiera';

  @override
  String get graphhopperServerUrlRequired =>
      'Ange server-URL innan verifiering.';

  @override
  String get successTitle => 'Framgång';

  @override
  String get graphhopperServerReachable => 'GraphHopper-server nåbar';

  @override
  String get errorTitle => 'Fel';

  @override
  String get close => 'Stäng';

  @override
  String get infoVersion => 'Version';

  @override
  String get infoProtocol => 'Protokoll';

  @override
  String get infoMaps => 'Kartor';

  @override
  String get infoRouting => 'Ruttning';

  @override
  String get infoSource => 'Källa';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (självhostad)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (moln)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get profileVisibilityTitle => 'Profilens synlighet';

  @override
  String get profileVisibilityDesc =>
      'Välj om andra Roadstr-användare ser ditt Nostr-namn, din npub och din bild.';

  @override
  String get profileVisibilityClear => 'Visa profilen offentligt';

  @override
  String get profileVisibilityPseudonymous => 'Pseudonymt läge (rekommenderas)';

  @override
  String get profileVisibilityPublishError =>
      'Valet sparades lokalt, men kunde ännu inte publiceras på Nostr.';

  @override
  String get nostrichLabel => 'nostrich';

  @override
  String get profileHiddenNotice =>
      'Den här användaren använder pseudonymt läge.';

  @override
  String get editSpeedLimit => 'Lägg till eller uppdatera hastighetsgräns';

  @override
  String get requestSpeedLimit => 'Föreslå en hastighetsgräns';

  @override
  String get acceptEditRequest => 'Acceptera förslaget';

  @override
  String get pendingEditRequests => 'Väntande ändringsförslag';

  @override
  String get speedLimitHint => 'Hastighetsgräns';

  @override
  String get speedLimitSaved => 'Hastighetsgränsen publicerad';

  @override
  String get editRequestSent => 'Förslaget skickat till rapportens författare';

  @override
  String get notConnected => 'Ej ansluten';

  @override
  String get loginWithNostrTitle => 'LOGGA IN MED NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Privat nyckel lämnar aldrig din enhet · Rekommenderas';

  @override
  String get nsecLoginOption => 'Infoga nsec';

  @override
  String get nsecLoginDescription =>
      'Privat nyckel lagras lokalt · Mindre säkert';

  @override
  String get connectedViaAmber => 'Ansluten via Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Ansluten via nsec';

  @override
  String get publicKeyLabel => 'OFFENTLIG NYCKEL';

  @override
  String get npubCopiedToClipboard => 'npub kopierad till urklipp';

  @override
  String get logoutTitle => 'Koppla från';

  @override
  String get logoutConfirmation =>
      'Ta bort Nostr-uppgifter från den här enheten?';

  @override
  String get logoutButton => 'Koppla från';

  @override
  String get nostrIdentityInfo =>
      'Med en Nostr-identitet kan du publicera trafikvarningar, rapporter och intressepunkter på ett decentraliserat sätt i Nostr-nätverket, utan centrala servrar.';

  @override
  String get warningTitle => 'Varning';

  @override
  String get nsecWarning =>
      'Du håller på att skriva in din Nostr-privata nyckel direkt i en app. Alla med fysisk eller fjärråtkomst till din enhet kan läsa den och permanent ta kontroll över din Nostr-identitet.';

  @override
  String get amberSecureMethodHint =>
      '✦  Den säkra metoden är Amber (NIP-55): nsec lämnar aldrig app-signerarvalvet.';

  @override
  String get nsecRiskAcknowledgment =>
      'Jag förstår risken och vill ändå fortsätta';

  @override
  String get continueButton => 'Fortsätt';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber är en NIP-55-kompatibel Android-appsignerare. Din privata nyckel förblir isolerad i Amber och delas aldrig.';

  @override
  String get requestKeyFromAmber => 'Begär offentlig nyckel från Amber';

  @override
  String get waitingForAmberResponse => 'Väntar på Amber-svar…';

  @override
  String get enterNsecTitle => 'Infoga nsec';

  @override
  String get loginButton => 'Logga in';

  @override
  String get invalidNpubTitle => 'Ogiltig npub';

  @override
  String get invalidNsecTitle => 'Ogiltig nsec';

  @override
  String get invalidNsecMessage => 'Kontrollera att du klistrade in rätt nsec.';

  @override
  String get ok => 'OK';

  @override
  String get gpsNotActiveTitle => 'GPS ej aktiv';

  @override
  String get gpsDisabledMessage =>
      'Aktivera GPS i enhetsinställningarna för att få din plats och använda navigering.';

  @override
  String get openSettings => 'Inställningar';

  @override
  String get myLocation => 'Min plats';

  @override
  String get loginToReport =>
      'Logga in med Nostr (Profil-sektion) för att rapportera händelser';

  @override
  String get navigateHere => 'Navigera hit';

  @override
  String get reportEventHere => 'Rapportera händelse här';

  @override
  String get zapSendSats => 'Zap ⚡ (skicka sats)';

  @override
  String get sendZap => 'Skicka en Zap';

  @override
  String get chooseAmountSats => 'Välj belopp i satoshi (sats):';

  @override
  String get customAmount => 'Anpassat belopp…';

  @override
  String get zapSending => 'Skickar…';

  @override
  String get fetchingLightningAddress => 'Hämtar Lightning-adress…';

  @override
  String get noLightningAddress =>
      'Den här rapportören har ingen Lightning-adress';

  @override
  String get requestingInvoice => 'Begär faktura…';

  @override
  String get lnurlUnavailable => 'LNURL ej tillgänglig';

  @override
  String get invoiceFailed => 'Kan inte generera faktura';

  @override
  String get openingWallet => 'Öppnar plånbok…';

  @override
  String get payingViaNwc => 'Betalar via NWC…';

  @override
  String get noLightningWallet => 'Ingen Lightning-plånbok hittades';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats skickade!';
  }

  @override
  String get stillThere => 'Finns fortfarande';

  @override
  String get notThereAnymore => 'Finns inte längre';

  @override
  String get loginToConfirm =>
      'Logga in med Nostr för att bekräfta eller bestrida';

  @override
  String get reportAnEvent => 'Rapportera en händelse';

  @override
  String get notifZapTitle => 'Zap mottagen';

  @override
  String notifZapBody(int sat) {
    return 'Du fick en zap på $sat sats!';
  }

  @override
  String get notifConfirmedTitle => 'Rapport bekräftad';

  @override
  String notifConfirmedBody(String category) {
    return 'Din $category-rapport bekräftades av en annan förare';
  }

  @override
  String get notifDeniedTitle => 'Rapport ifrågasatt';

  @override
  String notifDeniedBody(String category) {
    return 'Någon rapporterade att din $category inte längre finns kvar';
  }

  @override
  String chainedManeuver(String first, String second) {
    return '$first, sedan $second';
  }

  @override
  String thenManeuver(String instruction) {
    return 'sedan $instruction';
  }

  @override
  String get reportSpeedLimitHint => 'Hastighetsgräns (valfritt)';

  @override
  String get reportedSpeedLimit => 'Rapporterad hastighetsgräns';

  @override
  String speedCameraVoiceAlert(int limit, String unit) {
    return 'Fartkamera rapporterad, hastighetsgräns $limit $unit';
  }

  @override
  String get optionalComment => 'Valfri kommentar…';

  @override
  String get publish => 'Publicera';

  @override
  String get publishing => 'Publicerar…';

  @override
  String get reportPublished => 'Rapport publicerad ✓';

  @override
  String get myReports => 'MINA RAPPORTER';

  @override
  String get noReportsYet => 'Inga rapporter publicerade';

  @override
  String get zapBalance => 'Zap-saldo';

  @override
  String get satoshiFromReports => 'Satoshi mottagna från dina rapporter';

  @override
  String get reputationHigh => 'Hög';

  @override
  String get reputationMedium => 'Medel';

  @override
  String get reputationLow => 'Låg';

  @override
  String reputationLabel(String level) {
    return 'Rykte $level';
  }

  @override
  String reliability(int pct) {
    return 'Tillförlitlighet: $pct%';
  }

  @override
  String get confirmedLabel => 'Bekräftad';

  @override
  String get removedLabel => 'Borttagen';

  @override
  String get positionLabel => 'Position';

  @override
  String get loadingLabel => 'Laddar…';

  @override
  String get sectionWebSearch => 'Webbsökning';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Klistra in din NWC-URI (Alby Hub, Mutiny, Cashu…) för att betala Zaps direkt från appen.';

  @override
  String get searchEngineQwantDesc =>
      'Europeisk, integritetsfokuserad. Ingen spårning, inga annonsprofiler. Rekommenderas.';

  @override
  String get searchEngineBraveDesc =>
      'Oberoende index, öppen källkod. Inget Google- eller Bing-beroende. Noll profilering.';

  @override
  String get searchEngineDdgDesc =>
      'Integritetsfokuserad och populär. Resultat delvis från Bing — ha det i åtanke.';

  @override
  String get searchEngineStartpageDesc =>
      'Google-resultat utan att lämna dina data till Google. En rimlig kompromiss.';

  @override
  String get searchEngineGoogleDesc =>
      'Mycket effektivt. Men kom ihåg: Google känner dig bättre än din mamma och säljer allt till annonsörer. Ditt val. 🍪';

  @override
  String get categoryPolice => 'Polis';

  @override
  String get categorySpeedCamera => 'Fartkamera';

  @override
  String get categoryTrafficJam => 'Trafikstockning';

  @override
  String get categoryAccident => 'Olycka';

  @override
  String get categoryRoadClosure => 'Vägstängning';

  @override
  String get categoryConstruction => 'Vägarbete';

  @override
  String get categoryHazard => 'Fara';

  @override
  String get categoryRoadCondition => 'Väglag';

  @override
  String get categoryPothole => 'Väggropp';

  @override
  String get categoryFog => 'Dimma';

  @override
  String get categoryIce => 'Is';

  @override
  String get categoryAnimal => 'Djur';

  @override
  String get categoryOther => 'Övrigt';

  @override
  String get dateTimeLabel => 'Datum / tid';

  @override
  String minutesAgo(int count) {
    return 'för $count min sedan';
  }

  @override
  String hoursAgo(int count) {
    return 'för ${count}h sedan';
  }

  @override
  String daysAgo(int count) {
    return 'för ${count}d sedan';
  }

  @override
  String get sectionFavorites => 'Sparade platser';

  @override
  String get addFavorite => 'Lägg till plats';

  @override
  String get favoriteLabelHint => 'Namn (t.ex. Hem, Kontor)';

  @override
  String get favoriteAddressHint => 'Adress';

  @override
  String get favoriteGeocodingError =>
      'Adressen hittades inte. Försök med en mer specifik adress.';

  @override
  String get trafficAlertTitle => 'Ny trafik på rutten';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category rapporterat $age på din rutt.\n\nVill du hitta en alternativ rutt?';
  }

  @override
  String get trafficContinue => 'Fortsätt';

  @override
  String get trafficRecalculate => 'Beräkna om rutten';

  @override
  String get navExitTitle => 'Avsluta navigeringen?';

  @override
  String get navExitBody =>
      'Vill du stoppa navigeringen och återgå till kartan?';

  @override
  String get navContinue => 'Fortsätt navigeringen';

  @override
  String get navExit => 'Ja, avsluta';

  @override
  String get loadingInfo => 'Läser in information…';

  @override
  String get conditionsOnRoute => 'Förhållanden på rutten';

  @override
  String get calculateRoute => 'Beräkna rutt';

  @override
  String get sectionNavigationVoice => 'Navigeringsröst';

  @override
  String get voiceGuidance => 'Röstvägledning';

  @override
  String get voiceGuidanceDesc =>
      'Läs upp svängningsinstruktioner högt under navigering';

  @override
  String get voiceUnsupportedTitle => 'Röstvägledning är inte tillgänglig';

  @override
  String get voiceUnsupportedBody =>
      'Ditt språk stöds ännu inte för talade sväng­anvisningar. Navigeringsinstruktioner kommer fortfarande att visas som text på skärmen.';

  @override
  String get kokoroModelTitle => 'Röstmodell (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Inte nedladdat · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Laddar ner...';

  @override
  String get kokoroModelStatusReady => 'Röstmodell är redo';

  @override
  String get kokoroModelDownloadBtn => 'Ladda ner';

  @override
  String get kokoroModelSupportedLangs =>
      'Stödjer: italienska, engelska, spanska, franska, japanska, kinesiska, portugisiska';

  @override
  String get autoDarkMode => 'Automatiskt mörkt tema';

  @override
  String get autoDarkModeDesc => 'Aktiverar det mörka temat vid solnedgången';

  @override
  String get settingsImperialUnits => 'Imperiala enheter';

  @override
  String get settingsImperialUnitsDesc =>
      'Miles och fot istället för kilometer och meter';

  @override
  String get arrivedTitle => '🎉 Du är framme!';

  @override
  String get arrivalAhead => 'Du kommer att anlända till din destination';

  @override
  String get arrivalAheadLeft =>
      'Du kommer att anlända till din destination på vänster sida';

  @override
  String get arrivalAheadRight =>
      'Du kommer att anlända till din destination på höger sida';

  @override
  String continueForDistance(String distance) {
    return 'fortsätt rakt fram i $distance';
  }

  @override
  String get transportModeCar => 'Bil';

  @override
  String get transportModeWalk => 'Till fots';

  @override
  String etaArrivalLabel(String time) {
    return 'Ank. $time';
  }

  @override
  String get supportRoadstr => 'Stöd Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address kopierat till urklipp';
  }

  @override
  String get disclaimerTitle => 'Viktigt meddelande';

  @override
  String get disclaimerAccept => 'Jag har läst och accepterar';

  @override
  String get disclaimerBody =>
      'Roadstr är en experimentell, öppen källkod, community-underhållen navigationsapp baserad på OpenStreetMap-data och Nostr-protokollet, tillgänglig för användning i alla länder. Genom att ladda ner, installera eller använda denna app accepterar användaren villkorslöst samtliga följande villkor, utan territoriell begränsning.\n\n🚗 TRAFIKSÄKERHET FRÄMST\nFöraren måste alltid hålla blicken på vägen och följa alla tillämpliga trafikregler och uppsatta vägmärken, vilka alltid har företräde framför appens anvisningar. Hantera aldrig enheten under körning; montera den i en godkänd, väl synlig hållare innan du kör iväg, och avled aldrig uppmärksamheten från vägen för att interagera med den medan fordonet är i rörelse.\n\n⚠️ ÖVERTAGANDE AV RISK — VÄRLDEN ÖVER\nGenom att använda Roadstr, i vilket land och under vilket rättssystem som helst, tar användaren medvetet och frivilligt på sig ALLA risker som är förknippade med användningen, inklusive men inte begränsat till: trafikolyckor, personskada, dödsfall, sakskada, fordonsskada, böter, administrativa sanktioner, bogsering, beslag, straffrättsligt ansvar eller andra konsekvenser som direkt eller indirekt uppstår genom att förlita sig på appen. Användaren bär ensam det fulla ansvaret för varje beslut som rör körning och navigering.\n\n🚫 INGEN GARANTI\nRoadstr tillhandahålls strikt \"I BEFINTLIGT SKICK\" och \"I MÅN AV TILLGÄNGLIGHET\", utan någon som helst garanti, vare sig uttrycklig, underförstådd eller lagstadgad — inklusive, utan begränsning, garantier avseende riktighet, fullständighet, tillförlitlighet, tillgänglighet, säljbarhet, lämplighet för ett visst ändamål och icke-intrång. Kartdata, ruttplanering, hastighetsgränser, hastighetskameror och information om zoner med trafikbegränsning (ZTL/ZAC/LTZ) kommer från öppna, community-underhållna källor (OpenStreetMap, Overpass API) som när som helst och utan föregående meddelande kan vara ofullständiga, föråldrade eller felaktiga för vilket land, region eller kommun som helst. Användaren ansvarar ensam för att före och under resan självständigt kontrollera lagligheten och framkomligheten hos varje föreslagen rutt mot officiell lokal skyltning och lokala föreskrifter.\n\n📍 NOGGRANNHET OCH GPS\nGPS-positionering kan vara felaktig eller otillgänglig. Alla vägbeskrivningar, avstånd och varningar tillhandahålls endast som vägledning och får aldrig användas som ensam grund för ett körbeslut.\n\n🛡️ ANSVARSBEGRÄNSNING\nI den utsträckning som gällande lag i respektive jurisdiktion tillåter ska utvecklarna, bidragsgivarna och alla parter som varit involverade i att skapa eller distribuera Roadstr inte hållas ansvariga för någon direkt, indirekt, tillfällig, följd-, särskild, exemplarisk eller bestraffande skada av något slag — inklusive personskada, dödsfall eller ekonomisk förlust — som uppstår ur eller är relaterad till användningen av eller oförmågan att använda appen, även om de har informerats om möjligheten av sådana skador. Om en jurisdiktion inte tillåter hela eller delar av denna begränsning, ska ansvaret begränsas till den minsta omfattning som är tillåten enligt lag i den jurisdiktionen.\n\n🤝 GOTTGÖRELSE\nAnvändaren samtycker till att gottgöra och hålla utvecklarna och bidragsgivarna skadeslösa från alla anspråk, skador, förluster eller kostnader (inklusive juridiska avgifter) som uppstår till följd av användarens användning av appen eller brott mot dessa villkor.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ DELBARHET\nOm någon bestämmelse i dessa villkor visar sig vara ogenomförbar i en viss jurisdiktion ska den bestämmelsen begränsas eller strykas i den minsta omfattning som krävs, och samtliga återstående bestämmelser ska förbli i full kraft och verkan.\n\nGenom att använda Roadstr, var som helst i världen, bekräftar användaren att denne har läst, förstått och villkorslöst accepterat samtliga villkor ovan, och tar på sig fullt och fullständigt ansvar — samt all risk — för användningen av appen och alla konsekvenser som följer därav.';

  @override
  String get readOnWikipedia => 'Läs på Wikipedia';

  @override
  String get openInBrowser => 'Öppna i webbläsaren';

  @override
  String get wikipediaLoadFailed => 'Wikipedia kunde inte läsas in säkert.';

  @override
  String get retry => 'Försök igen';

  @override
  String get poiDetailsFromOsm => 'Information från OpenStreetMap';

  @override
  String get poiCategory => 'Kategori';

  @override
  String get poiOperator => 'Operatör';

  @override
  String get poiCuisine => 'Kök';

  @override
  String get poiWheelchairYes => 'Rullstolsanpassat';

  @override
  String get poiWheelchairLimited => 'Begränsad rullstolstillgänglighet';

  @override
  String get poiWheelchairNo => 'Inte rullstolsanpassat';

  @override
  String get poiContact => 'Kontakt';

  @override
  String get poiAddress => 'Adress';

  @override
  String get poiWebsite => 'Webbplats';

  @override
  String get poiAccessPrivate => 'Privat tillträde';

  @override
  String get poiAccessCustomers => 'Endast kunder';

  @override
  String get poiAccessPermit => 'Tillstånd krävs';

  @override
  String get poiAccessNo => 'Ingen allmän åtkomst';

  @override
  String get poiAccessDestination => 'Endast destinationstrafik';

  @override
  String get poiLightningAccepted => 'Lightning accepteras';

  @override
  String get poiBitcoinAccepted => 'Bitcoin accepteras';

  @override
  String get poiParkingDetails => 'Parkering';

  @override
  String get poiParkingSurface => 'Markparkering';

  @override
  String get poiParkingUnderground => 'Underjordisk';

  @override
  String get poiParkingMultiStorey => 'Parkeringshus';

  @override
  String get poiParkingStreetSide => 'Längs gatan';

  @override
  String get poiParkingLane => 'På gatan';

  @override
  String get poiParkingRooftop => 'På taket';

  @override
  String get poiFee => 'Avgift';

  @override
  String get poiFree => 'Gratis';

  @override
  String get poiPaid => 'Avgiftsbelagd';

  @override
  String get poiCapacity => 'Kapacitet';

  @override
  String get poiMaxStay => 'Längsta parkeringstid';

  @override
  String get poiPrice => 'Pris';

  @override
  String get poiChargingDetails => 'Laddning';

  @override
  String get poiConnectorType2 => 'Typ 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Diesel';

  @override
  String get poiPetrol95 => 'Bensin 95';

  @override
  String get poiSmokingAllowed => 'Rökning tillåten';

  @override
  String get poiSmokingOutside => 'Rökning utomhus';

  @override
  String get poiSmokingAreas => 'Rökområden';

  @override
  String get poiSmokeFree => 'Rökfritt';

  @override
  String get poiOutdoorSeating => 'Uteservering';

  @override
  String get poiTakeaway => 'Hämtmat';

  @override
  String get poiTakeawayOnly => 'Endast hämtmat';

  @override
  String get gpsSignalLost => 'GPS-signalen förlorad';

  @override
  String get poiOpenNow => 'Öppet nu';

  @override
  String get poiClosedNow => 'Stängt';

  @override
  String poiOpensAt(String when) {
    return 'Öppnar: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Stänger: $when';
  }

  @override
  String searchOnEngine(String engine) {
    return 'Sök på $engine';
  }

  @override
  String get plannerFromHint => 'Från…';

  @override
  String get plannerToHint => 'Destination…';

  @override
  String departEta(String dep, String arr) {
    return 'Avresa $dep  →  Ankomst $arr';
  }

  @override
  String get modeCar => 'Bil';

  @override
  String get modeBike => 'Cykel';

  @override
  String get modeWalk => 'Till fots';

  @override
  String windSpeed(String speed) {
    return 'vind $speed';
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
  String get weatherClear => 'Klart';

  @override
  String get weatherPartlyCloudy => 'Delvis molnigt';

  @override
  String get weatherCloudy => 'Molnigt';

  @override
  String get weatherFog => 'Dimma';

  @override
  String get weatherLightRain => 'Lätt regn';

  @override
  String get weatherRain => 'Regn';

  @override
  String get weatherSnow => 'Snö';

  @override
  String get weatherShowers => 'Skurar';

  @override
  String get weatherThunderstorm => 'Åska';

  @override
  String get ztlInsideWarning => 'Begränsat trafikområde';

  @override
  String get onboardingAppSubtitle => 'Öppen källkod Nostr-navigation';

  @override
  String get onboardingWelcomeTitle => 'Välkommen';

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
  String get onboardingGetStarted => 'Kom igång';

  @override
  String get onboardingNostrTitle => 'Nostr-identitet';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Ansluten';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (rekommenderas)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec-nyckel';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Ogiltig nsec-nyckel — kontrollera och försök igen.';

  @override
  String get onboardingSkip => 'Hoppa över nu';

  @override
  String get onboardingContinue => 'Fortsätt';

  @override
  String get onboardingEnterNsec => 'Ange nsec-nyckel';

  @override
  String get onboardingSetupTitle => 'Konfigurera Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Plats';

  @override
  String get onboardingLocationGranted => 'Platsåtkomst beviljad';

  @override
  String get onboardingLocationRequired => 'Krävs för GPS-navigation';

  @override
  String get onboardingGrantButton => 'Bevilja';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS-användare';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

  @override
  String get onboardingVoiceTitle => 'AI-röststyrning (valfritt)';

  @override
  String get onboardingVoiceReady => 'Kokoro röstmodell klar';

  @override
  String get onboardingVoiceDownloading => 'Laddar ned röstmodell…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Ladda ned';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Du är redo att navigera!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Kör!';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Röst';

  @override
  String get kokoroVoiceFemale => 'Kvinnlig';

  @override
  String get kokoroVoiceMale => 'Manlig';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Manlig röst är inte tillgänglig för det här språket';

  @override
  String get kokoroSpeedTitle => 'Talhastighet';

  @override
  String get kokoroVolumeTitle => 'Röstvolym';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Valfritt: dina sparade favoriter kan synkroniseras mellan dina enheter via Nostr-reläerna, end-to-end-krypterade (NIP-44) med din egen nyckel — reläerna ser bara krypterad text och ingen utom du kan läsa innehållet. Aktivera när som helst i Inställningar.';

  @override
  String get profileVisibilityOnboarding =>
      'Roadstr publicerar alltid den publika Nostr-nyckel som behövs för att verifiera en rapport. I pseudonymt läge ser andra Roadstr-användare bara en allmän avatar och ”nostrich”; i offentligt läge ser de även ditt Nostr-namn, din npub och din profilbild. Dina rapporter, positioner, ditt rykte och mottagna zaps förblir offentliga på Nostr.';

  @override
  String get parkingSaveHere => 'Spara parkering här';

  @override
  String get parkingLongPressConfirm => 'Spara den här platsen som parkering?';

  @override
  String get parkingMarkerTitle => 'Parkeringsplats';

  @override
  String get parkingNavigateHere => 'Navigera till parkeringen';

  @override
  String get parkingRemove => 'Ta bort parkering';

  @override
  String get parkingSavedSnack => 'Parkeringsplats sparad';

  @override
  String get parkingRemovedSnack => 'Parkeringsplats borttagen';

  @override
  String get exportFavoritesTitle => 'Exportera favoriter';

  @override
  String get exportFavoritesDesc =>
      'Spara dina favoritplatser i en JSON-fil som du kan säkerhetskopiera eller flytta till en annan enhet.';

  @override
  String get exportEncryptToggle => 'Kryptera med lösenord';

  @override
  String get exportPasswordHint => 'Lösenord';

  @override
  String get exportButton => 'Exportera';

  @override
  String get exportSuccessSnack => 'Favoriter exporterade';

  @override
  String get exportFailedSnack => 'Exporten misslyckades';

  @override
  String get importFavoritesTitle => 'Importera favoriter';

  @override
  String get importPasswordPrompt =>
      'Den här filen är krypterad — ange lösenordet';

  @override
  String importSuccessSnack(int n) {
    return '$n favoriter importerade';
  }

  @override
  String get importFailedSnack =>
      'Importen misslyckades — kontrollera filen och lösenordet';

  @override
  String get syncFavoritesTitle => 'Synkronisera favoriter';

  @override
  String get syncFavoritesDesc =>
      'Publicera dina favoriter, end-to-end-krypterade, till dina Nostr-reläer så att de följer dig på alla enheter. Reläerna ser bara krypterad text — ingen utom du kan läsa innehållet.';

  @override
  String get syncNowButton => 'Skicka till Nostr';

  @override
  String get syncPullButton => 'Hämta från Nostr';

  @override
  String get syncPassphraseTitle => 'Synkroniseringslösenfras (valfri)';

  @override
  String get syncPassphraseDesc =>
      'Andra krypteringslagret för synkroniserade favoriter: även om din Nostr-nyckel skulle komprometteras förblir ögonblicksbilden på reläerna oläslig utan denna lösenfras. Du anger den en gång på varje ny enhet. Lämna tomt för att inaktivera.';

  @override
  String get syncCustomRelayTitle => 'Ditt eget relä (valfritt)';

  @override
  String get syncCustomRelayDesc =>
      'Lägg till ett relä som du driver eller betalar för, så publiceras dina favoriter även där. Publika reläer lovar ingenting om hur länge de sparar data; ditt eget gör det. Lämna tomt för att bara använda de inbyggda reläerna.';

  @override
  String get syncCustomRelayInvalid =>
      'Ogiltig reläadress. Den måste börja med wss://';

  @override
  String get syncSuccessSnack => 'Favoriter synkroniserade';

  @override
  String get syncFailedSnack => 'Synkroniseringen misslyckades';

  @override
  String syncLastSyncLabel(String when) {
    return 'Senast synkroniserad: $when';
  }

  @override
  String get syncNoIdentity =>
      'Logga in med Nostr (Inställningar → Profil) för att aktivera synkronisering';

  @override
  String get onboardingVpnNotice =>
      'För maximal integritet — rapporter sprids via Nostr-nätverket — rekommenderas användning av VPN. Mullvad, som kan betalas med Bitcoin, är det rekommenderade valet.';

  @override
  String get trafficNormal => 'Normal trafik';

  @override
  String get trafficModerate => 'Måttlig trafik';

  @override
  String get trafficHeavy => 'Tät trafik';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get avoidHighwaysAndTolls => 'Undvik motorvägar och vägtullar';

  @override
  String get avoidRouteUnavailable =>
      'Ingen rutt som undviker både motorvägar och avgiftsbelagda vägar hittades.';

  @override
  String get avoidanceUnavoidableSection =>
      'Minimerar motorvägar/vägtullar · oundviklig sträcka';

  @override
  String get nearbyTitle => 'I närheten';

  @override
  String get nearbyNeedsGps => 'Väntar på GPS';

  @override
  String get nearbyNothingFound => 'Inget hittades inom 5 km';

  @override
  String get nearbyFuel => 'Drivmedel';

  @override
  String get nearbySupermarket => 'Livsmedelsbutik';

  @override
  String get nearbyAtm => 'Uttagsautomat';

  @override
  String get nearbyPharmacy => 'Apotek';

  @override
  String get nearbyHospital => 'Sjukhus';

  @override
  String get nearbyPolice => 'Polis';

  @override
  String get nearbyPostOffice => 'Post';

  @override
  String get nearbyParking => 'Parkering';

  @override
  String get nearbyCharging => 'Laddning';

  @override
  String get transportModeTransit => 'Kollektivt';

  @override
  String get transitNoServiceHere =>
      'Inga kollektivtrafikdata för detta område';

  @override
  String get transitRequestFailed => 'Kunde inte ladda kollektivtrafik';

  @override
  String get transitRetry => 'Försök igen';

  @override
  String get transitWalkLeg => 'Gå';

  @override
  String get transitYourLocation => 'Din plats';

  @override
  String get transitDestination => 'Destination';

  @override
  String get transitScheduledTimes =>
      'Tiderna kommer från publicerade tidtabeller';

  @override
  String get transitDirect => 'Direkt';

  @override
  String transitTransfers(int count) {
    return '$count byten';
  }

  @override
  String ztlNearRouteWarning(String street) {
    return 'Varning: $street är en zon med begränsad trafik';
  }

  @override
  String get themeModernNostr => 'Modern · Nostr-lila';

  @override
  String get themeModernBitcoin => 'Modern · Bitcoin-orange';

  @override
  String get themeModernDarkNostr => 'Modern mörk · Nostr-lila';

  @override
  String get themeModernDarkBitcoin => 'Modern mörk · Bitcoin-orange';

  @override
  String get plannerAddStop => 'Lägg till stopp';

  @override
  String get plannerStopHint => 'Stopp på vägen';

  @override
  String get plannerStopsFull => 'Inga fler stopp kan läggas till';

  @override
  String plannerStopAdded(String place) {
    return 'Stopp tillagt: $place';
  }
}
