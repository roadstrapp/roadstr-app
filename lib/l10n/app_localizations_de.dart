// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for German (`de`).
class AppLocalizationsDe extends AppLocalizations {
  AppLocalizationsDe([String locale = 'de']) : super(locale);

  @override
  String get searchHint => 'Wohin möchten Sie fahren?';

  @override
  String get calculatingRoute => 'Route wird berechnet…';

  @override
  String get routeNotFoundTitle => 'Route nicht gefunden';

  @override
  String get noRouteFound =>
      'Keine Route gefunden. Überprüfen Sie Ihre Verbindung.';

  @override
  String get emptyServerResponse =>
      'Leere Serverantwort. Überprüfen Sie Ihre Verbindung.';

  @override
  String routeError(String error) {
    return 'Fehler bei der Routenberechnung: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS nicht verfügbar — Einstellungen → App → Roadstr → Berechtigungen → Standort';

  @override
  String get acquiringGps => 'GPS wird ermittelt…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopper-Server nicht konfiguriert — OSRM wird verwendet';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper-API-Schlüssel nicht konfiguriert — OSRM wird verwendet';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService-API-Schlüssel nicht konfiguriert — OSRM wird verwendet';

  @override
  String get chooseRoute => 'Route wählen';

  @override
  String routeOptionsCount(int count) {
    return '$count Optionen';
  }

  @override
  String get cancel => 'Abbrechen';

  @override
  String get startNavigation => 'Navigation starten';

  @override
  String get fastestRoute => 'Schnellste';

  @override
  String get now => 'Jetzt';

  @override
  String get history => 'Verlauf';

  @override
  String get clearHistory => 'Löschen';

  @override
  String get selectedPosition => 'Ausgewählte Position';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Menü';

  @override
  String get settingsTitle => 'Einstellungen';

  @override
  String get sectionTheme => 'Design';

  @override
  String get sectionMap => 'Karte';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Sprache';

  @override
  String get themeLightNostr => 'Hell · Nostr Violett';

  @override
  String get themeLightBitcoin => 'Hell · Bitcoin Orange';

  @override
  String get themeDarkNostr => 'Dunkel · Nostr Violett';

  @override
  String get themeDarkBitcoin => 'Dunkel · Bitcoin Orange';

  @override
  String get langSystem => 'Systemstandard';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Bildschirm eingeschaltet lassen';

  @override
  String get keepScreenOnDescription =>
      'Verhindert den Ruhezustand während der Navigation';

  @override
  String get rotateMap => 'Karte folgt Richtung';

  @override
  String get rotateMapDescription =>
      'Karte dreht sich entsprechend der Fahrtrichtung';

  @override
  String get mapTileUrlLabel => 'Kachel-URL der Karte';

  @override
  String get routingProviderLabel => 'Routing-Anbieter';

  @override
  String get osrmProvider => 'OSRM (öffentlich, kein Schlüssel erforderlich)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokal/privat)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API-Schlüssel)';

  @override
  String get openrouteProvider => 'OpenRouteService (API-Schlüssel)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper-API-Schlüssel (optional)';

  @override
  String get verify => 'Überprüfen';

  @override
  String get graphhopperServerUrlRequired =>
      'Geben Sie die Server-URL ein, bevor Sie überprüfen.';

  @override
  String get successTitle => 'Erfolg';

  @override
  String get graphhopperServerReachable => 'GraphHopper-Server erreichbar';

  @override
  String get errorTitle => 'Fehler';

  @override
  String get close => 'Schließen';

  @override
  String get infoVersion => 'Version';

  @override
  String get infoProtocol => 'Protokoll';

  @override
  String get infoMaps => 'Karten';

  @override
  String get infoRouting => 'Routing';

  @override
  String get infoSource => 'Quelle';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (selbst gehostet)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (Cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Nicht verbunden';

  @override
  String get loginWithNostrTitle => 'MIT NOSTR ANMELDEN';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Privater Schlüssel verlässt nie Ihr Gerät · Empfohlen';

  @override
  String get nsecLoginOption => 'nsec eingeben';

  @override
  String get nsecLoginDescription =>
      'Privater Schlüssel wird lokal gespeichert · Weniger sicher';

  @override
  String get connectedViaAmber => 'Verbunden über Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Verbunden über nsec';

  @override
  String get publicKeyLabel => 'ÖFFENTLICHER SCHLÜSSEL';

  @override
  String get npubCopiedToClipboard => 'npub in die Zwischenablage kopiert';

  @override
  String get logoutTitle => 'Trennen';

  @override
  String get logoutConfirmation =>
      'Nostr-Anmeldedaten von diesem Gerät entfernen?';

  @override
  String get logoutButton => 'Trennen';

  @override
  String get nostrIdentityInfo =>
      'Mit einer Nostr-Identität können Sie Verkehrsmeldungen, Berichte und Sehenswürdigkeiten dezentral im Nostr-Netzwerk veröffentlichen, ohne zentrale Server.';

  @override
  String get warningTitle => 'Warnung';

  @override
  String get nsecWarning =>
      'Sie sind dabei, Ihren privaten Nostr-Schlüssel direkt in eine App einzugeben. Jeder mit physischem oder Remote-Zugriff auf Ihr Gerät könnte ihn lesen und dauerhaft die Kontrolle über Ihre Nostr-Identität übernehmen.';

  @override
  String get amberSecureMethodHint =>
      '✦  Die sichere Methode ist Amber (NIP-55): der nsec verlässt nie den App-Signer-Tresor.';

  @override
  String get nsecRiskAcknowledgment =>
      'Ich verstehe das Risiko und möchte trotzdem fortfahren';

  @override
  String get continueButton => 'Weiter';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber ist eine NIP-55-konforme Android-App zum Signieren. Ihr privater Schlüssel bleibt in Amber isoliert und wird niemals weitergegeben.';

  @override
  String get requestKeyFromAmber =>
      'Öffentlichen Schlüssel von Amber anfordern';

  @override
  String get amberNotFound =>
      'Amber nicht gefunden. Installieren Sie es aus dem Play Store oder geben Sie Ihren npub manuell ein.';

  @override
  String get waitingForAmberResponse => 'Warte auf Amber-Antwort…';

  @override
  String get pasteNpubManually => 'Fügen Sie Ihren npub manuell ein:';

  @override
  String get confirmNpub => 'npub bestätigen';

  @override
  String get enterNsecTitle => 'nsec eingeben';

  @override
  String get loginButton => 'Anmelden';

  @override
  String get invalidNpubTitle => 'Ungültiger npub';

  @override
  String get invalidNsecTitle => 'Ungültiger nsec';

  @override
  String get invalidNpubMessage =>
      'Stellen Sie sicher, dass Sie den richtigen npub eingefügt haben.';

  @override
  String get invalidNsecMessage =>
      'Stellen Sie sicher, dass Sie den richtigen nsec eingefügt haben.';

  @override
  String get amberResponseError => 'Amber-Antwortfehler';

  @override
  String get ok => 'OK';

  @override
  String get or => 'oder';

  @override
  String get gpsNotActiveTitle => 'GPS nicht aktiv';

  @override
  String get gpsDisabledMessage =>
      'Aktivieren Sie GPS in Ihren Geräteeinstellungen, um Ihren Standort zu ermitteln und die Navigation zu nutzen.';

  @override
  String get openSettings => 'Einstellungen';

  @override
  String get myLocation => 'Mein Standort';

  @override
  String get loginToReport =>
      'Mit Nostr anmelden (Profilbereich), um Ereignisse zu melden';

  @override
  String get navigateHere => 'Hierher navigieren';

  @override
  String get reportEventHere => 'Ereignis hier melden';

  @override
  String get zapSendSats => 'Zap ⚡ (Sats senden)';

  @override
  String get sendZap => 'Einen Zap senden';

  @override
  String get chooseAmountSats => 'Betrag in Satoshi (Sats) wählen:';

  @override
  String get customAmount => 'Benutzerdefinierter Betrag…';

  @override
  String get zapSending => 'Wird gesendet…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Lightning-Adresse wird abgerufen…';

  @override
  String get noLightningAddress => 'Dieser Melder hat keine Lightning-Adresse';

  @override
  String get requestingInvoice => 'Rechnung wird angefordert…';

  @override
  String get lnurlUnavailable => 'LNURL nicht verfügbar';

  @override
  String get invoiceFailed => 'Rechnung konnte nicht erstellt werden';

  @override
  String get openingWallet => 'Wallet wird geöffnet…';

  @override
  String get payingViaNwc => 'Zahlung über NWC…';

  @override
  String get noLightningWallet => 'Keine Lightning-Wallet gefunden';

  @override
  String zapSent(int sats) {
    return '⚡ $sats Sats gesendet!';
  }

  @override
  String get stillThere => 'Noch vorhanden';

  @override
  String get notThereAnymore => 'Nicht mehr vorhanden';

  @override
  String get loginToConfirm =>
      'Mit Nostr anmelden, um zu bestätigen oder anzufechten';

  @override
  String get reportAnEvent => 'Ereignis melden';

  @override
  String get optionalComment => 'Optionaler Kommentar…';

  @override
  String get publish => 'Veröffentlichen';

  @override
  String get publishing => 'Wird veröffentlicht…';

  @override
  String get reportPublished => 'Bericht veröffentlicht ✓';

  @override
  String get myReports => 'MEINE BERICHTE';

  @override
  String get noReportsYet => 'Keine Berichte veröffentlicht';

  @override
  String get zapBalance => 'Zap-Guthaben';

  @override
  String get satoshiFromReports => 'Empfangene Satoshi aus Ihren Berichten';

  @override
  String get reputationHigh => 'Hoch';

  @override
  String get reputationMedium => 'Mittel';

  @override
  String get reputationLow => 'Niedrig';

  @override
  String reputationLabel(String level) {
    return 'Reputation $level';
  }

  @override
  String reliability(int pct) {
    return 'Zuverlässigkeit: $pct%';
  }

  @override
  String get confirmedLabel => 'Bestätigt';

  @override
  String get removedLabel => 'Entfernt';

  @override
  String get positionLabel => 'Position';

  @override
  String get loadingLabel => 'Lädt…';

  @override
  String get sectionWebSearch => 'Websuche';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Fügen Sie Ihren NWC-URI (Alby Hub, Mutiny, Cashu…) ein, um Zaps direkt aus der App zu bezahlen.';

  @override
  String get searchEngineQwantDesc =>
      'Europäisch, datenschutzorientiert. Kein Tracking, keine Werbeprofile. Empfohlen.';

  @override
  String get searchEngineBraveDesc =>
      'Unabhängiger Index, Open-Source. Keine Google- oder Bing-Abhängigkeit. Kein Profiling.';

  @override
  String get searchEngineDdgDesc =>
      'Datenschutzorientiert und beliebt. Ergebnisse teilweise von Bing — das sollte man im Hinterkopf behalten.';

  @override
  String get searchEngineStartpageDesc =>
      'Google-Ergebnisse ohne Ihre Daten an Google weiterzugeben. Ein vernünftiger Kompromiss.';

  @override
  String get searchEngineGoogleDesc =>
      'Sehr effektiv. Aber denken Sie daran: Google kennt Sie besser als Ihre Mutter und verkauft alles an Werbetreibende. Ihre Entscheidung. 🍪';

  @override
  String get categoryPolice => 'Polizei';

  @override
  String get categorySpeedCamera => 'Blitzer';

  @override
  String get categoryTrafficJam => 'Stau';

  @override
  String get categoryAccident => 'Unfall';

  @override
  String get categoryRoadClosure => 'Straßensperrung';

  @override
  String get categoryConstruction => 'Baustelle';

  @override
  String get categoryHazard => 'Gefahr';

  @override
  String get categoryRoadCondition => 'Straßenzustand';

  @override
  String get categoryPothole => 'Schlagloch';

  @override
  String get categoryFog => 'Nebel';

  @override
  String get categoryIce => 'Eis';

  @override
  String get categoryAnimal => 'Tier';

  @override
  String get categoryOther => 'Sonstiges';

  @override
  String get dateTimeLabel => 'Datum / Uhrzeit';

  @override
  String minutesAgo(int count) {
    return 'Vor $count Min.';
  }

  @override
  String hoursAgo(int count) {
    return 'Vor $count Std.';
  }

  @override
  String daysAgo(int count) {
    return 'Vor $count Tagen';
  }

  @override
  String get sectionFavorites => 'Gespeicherte Orte';

  @override
  String get addFavorite => 'Ort hinzufügen';

  @override
  String get favoriteLabelHint => 'Name (z. B. Zuhause, Büro)';

  @override
  String get favoriteAddressHint => 'Adresse';

  @override
  String get favoriteGeocodingError =>
      'Adresse nicht gefunden. Versuche eine genauere Adresse.';

  @override
  String get trafficAlertTitle => 'Neuer Verkehr auf der Route';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category gemeldet $age auf Ihrer Route.\n\nMöchten Sie eine alternative Route finden?';
  }

  @override
  String get trafficContinue => 'Weiter';

  @override
  String get trafficRecalculate => 'Route neu berechnen';

  @override
  String get navExitTitle => 'Navigation beenden?';

  @override
  String get navExitBody =>
      'Möchten Sie die Navigation beenden und zur Karte zurückkehren?';

  @override
  String get navContinue => 'Navigation fortsetzen';

  @override
  String get navExit => 'Ja, beenden';

  @override
  String get loadingInfo => 'Informationen laden…';

  @override
  String get conditionsOnRoute => 'Bedingungen auf der Route';

  @override
  String get calculateRoute => 'Route berechnen';

  @override
  String get sectionNavigationVoice => 'Navigationsstimme';

  @override
  String get voiceGuidance => 'Sprachführung';

  @override
  String get voiceGuidanceDesc =>
      'Abbiegehinweise während der Navigation laut vorlesen';

  @override
  String get testVoiceEngine => 'Sprach-Engine testen';

  @override
  String get testVoiceEngineDesc =>
      'Tippen, um die TTS-Engine zu prüfen und Einrichtungshinweise zu erhalten';

  @override
  String get ttsDialogTitle => 'Sprach-Engine fehlt';

  @override
  String get ttsDialogBody =>
      'Es wurde keine funktionierende Text-to-Speech-Engine gefunden.\n\nRoadstr setzt ausschließlich auf Open-Source-Software — installiere eine dieser kostenlosen Engines von F-Droid:';

  @override
  String get ttsRhvoiceDesc =>
      'Natürlich klingende Stimme, eingeschränkte Sprachauswahl';

  @override
  String get ttsEspeakDesc => 'Deckt über 100 Sprachen ab, roboterhafte Stimme';

  @override
  String get ttsInstallNote =>
      '⚠️ Nach der Installation:\n1. Android-Einstellungen → Bedienungshilfen → Text-in-Sprache-Ausgabe\n2. Die gerade installierte Engine auswählen\n3. Sprachdaten für deine Sprache herunterladen\n4. Roadstr vollständig neu starten';

  @override
  String get ttsTestNow => 'Jetzt testen';

  @override
  String get voiceUnsupportedTitle => 'Sprachführung nicht verfügbar';

  @override
  String get voiceUnsupportedBody =>
      'Deine Sprache wird für gesprochene Abbiegehinweise noch nicht unterstützt. Die Navigationsanweisungen werden weiterhin als Text auf dem Bildschirm angezeigt.';

  @override
  String get kokoroModelTitle => 'Sprachmodell (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Nicht heruntergeladen · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Herunterladen...';

  @override
  String get kokoroModelStatusReady => 'Sprachmodell bereit';

  @override
  String get kokoroModelDownloadBtn => 'Herunterladen';

  @override
  String get kokoroModelSupportedLangs =>
      'Unterstützt: Italienisch, Englisch, Spanisch, Französisch, Japanisch, Chinesisch, Portugiesisch';

  @override
  String get autoDarkMode => 'Automatischer Dunkelmodus';

  @override
  String get autoDarkModeDesc =>
      'Aktiviert das dunkle Design bei Sonnenuntergang';
}
