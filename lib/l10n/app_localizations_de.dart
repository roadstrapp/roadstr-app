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
  String get bottomBarNotifications => 'Notifications';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Menü';

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
  String get autoCenterOnLaunch => 'Beim Start auf meinen Standort zentrieren';

  @override
  String get autoCenterOnLaunchDesc =>
      'Verwendet GPS nur dann automatisch, wenn die Standortberechtigung bereits erteilt wurde';

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
  String get notifZapTitle => 'Zap erhalten';

  @override
  String notifZapBody(int sat) {
    return 'Du hast einen Zap über $sat Sats erhalten!';
  }

  @override
  String get notifConfirmedTitle => 'Meldung bestätigt';

  @override
  String notifConfirmedBody(String category) {
    return 'Deine $category-Meldung wurde von einem anderen Fahrer bestätigt';
  }

  @override
  String get notifDeniedTitle => 'Meldung angezweifelt';

  @override
  String notifDeniedBody(String category) {
    return 'Jemand hat gemeldet, dass dein $category nicht mehr da ist';
  }

  @override
  String chainedManeuver(String first, String second) {
    return '$first, dann $second';
  }

  @override
  String get reportSpeedLimitHint => 'Tempolimit (optional)';

  @override
  String get reportedSpeedLimit => 'Gemeldetes Tempolimit';

  @override
  String speedCameraVoiceAlert(int limit, String unit) {
    return 'Blitzer gemeldet, Tempolimit $limit $unit';
  }

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

  @override
  String get settingsImperialUnits => 'Imperiale Einheiten';

  @override
  String get settingsImperialUnitsDesc =>
      'Meilen und Fuß statt Kilometer und Meter';

  @override
  String get arrivedTitle => '🎉 Angekommen!';

  @override
  String get arrivedBody => 'Sie haben Ihr Ziel erreicht.';

  @override
  String get arrivedFeedbackPrompt => 'Wie war die Fahrt?';

  @override
  String get feedbackBad => 'Schlecht';

  @override
  String get feedbackGood => 'Gut!';

  @override
  String get feedbackDialogTitle => 'Sagen Sie uns, was schiefgelaufen ist';

  @override
  String get feedbackHint => 'Beschreiben Sie das Problem…';

  @override
  String get feedbackSent => 'Feedback gesendet — danke! 🙏';

  @override
  String get feedbackSubmit => 'Senden';

  @override
  String get transportModeCar => 'Auto';

  @override
  String get transportModeWalk => 'Zu Fuß';

  @override
  String etaArrivalLabel(String time) {
    return 'Ank. $time';
  }

  @override
  String get supportRoadstr => 'Roadstr unterstützen';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address in Zwischenablage kopiert';
  }

  @override
  String get disclaimerTitle => 'Wichtiger Hinweis';

  @override
  String get disclaimerAccept => 'Ich habe gelesen und akzeptiere';

  @override
  String get disclaimerBody =>
      'Roadstr ist eine experimentelle, quelloffene und von der Community betriebene Navigations-App, die auf OpenStreetMap-Daten und dem Nostr-Protokoll basiert und zur Nutzung in jedem Land bereitgestellt wird. Durch das Herunterladen, die Installation oder die Nutzung dieser App akzeptiert der Nutzer bedingungslos sämtliche der folgenden Bedingungen, ohne räumliche Beschränkung.\n\n🚗 VERKEHRSSICHERHEIT GEHT VOR\nDer Fahrer muss stets den Blick auf die Straße gerichtet halten und alle geltenden Verkehrsgesetze sowie die vorhandene Beschilderung beachten, die stets Vorrang vor jeder Anweisung der App haben. Das Gerät darf während der Fahrt niemals bedient werden; es ist vor Fahrtantritt in einer zugelassenen, gut sichtbaren Halterung zu befestigen, und die Aufmerksamkeit darf während der Fahrt niemals von der Straße abgewendet werden, um mit dem Gerät zu interagieren.\n\n⚠️ ÜBERNAHME DES RISIKOS — WELTWEIT\nDurch die Nutzung von Roadstr, in jedem Land und unter jedem Rechtssystem, übernimmt der Nutzer wissentlich und freiwillig SÄMTLICHE mit der Nutzung verbundenen Risiken, einschließlich, aber nicht beschränkt auf: Verkehrsunfälle, Personenschäden, Tod, Sachschäden, Fahrzeugschäden, Bußgelder, Verwaltungsstrafen, Abschleppen, Beschlagnahmung, strafrechtliche Haftung oder jede andere Folge, die sich direkt oder indirekt aus dem Vertrauen auf die App ergibt. Der Nutzer allein trägt die volle Verantwortung für jede Fahr- und Navigationsentscheidung.\n\n🚫 KEINE GEWÄHRLEISTUNG\nRoadstr wird ausschließlich „WIE BESEHEN\" und „WIE VERFÜGBAR\" bereitgestellt, ohne jegliche Gewährleistung, weder ausdrücklich noch stillschweigend noch gesetzlich vorgeschrieben — einschließlich, aber nicht beschränkt auf Gewährleistungen der Genauigkeit, Vollständigkeit, Zuverlässigkeit, Verfügbarkeit, Marktgängigkeit, Eignung für einen bestimmten Zweck und Nichtverletzung von Rechten Dritter. Kartendaten, Routenberechnung, Geschwindigkeitsbegrenzungen, Blitzer und Informationen zu verkehrsbeschränkten Zonen (ZTL/ZAC/LTZ) stammen aus offenen, von der Community gepflegten Quellen (OpenStreetMap, Overpass API), die für jedes Land, jede Region oder Gemeinde jederzeit und ohne vorherige Ankündigung unvollständig, veraltet oder ungenau sein können. Der Nutzer ist allein dafür verantwortlich, vor und während der Fahrt die Rechtmäßigkeit und Befahrbarkeit jeder vorgeschlagenen Route eigenständig anhand der offiziellen örtlichen Beschilderung und Vorschriften zu überprüfen.\n\n📍 GENAUIGKEIT & GPS\nDie GPS-Positionsbestimmung kann ungenau oder nicht verfügbar sein. Sämtliche Wegbeschreibungen, Entfernungsangaben und Hinweise dienen ausschließlich zur Orientierung und dürfen niemals als alleinige Grundlage für eine Fahrentscheidung herangezogen werden.\n\n🛡️ HAFTUNGSBESCHRÄNKUNG\nSoweit nach geltendem Recht in der jeweiligen Rechtsordnung maximal zulässig, haften die Entwickler, Mitwirkenden und alle an der Erstellung oder Verbreitung von Roadstr beteiligten Parteien nicht für direkte, indirekte, beiläufig entstandene, Folge-, besondere, exemplarische oder Strafschäden jeglicher Art — einschließlich Personenschäden, Tod oder finanzieller Verluste —, die sich aus der Nutzung oder der Unmöglichkeit der Nutzung der App ergeben, selbst wenn auf die Möglichkeit solcher Schäden hingewiesen wurde. Soweit eine Rechtsordnung diese Haftungsbeschränkung ganz oder teilweise nicht zulässt, ist die Haftung auf das nach dem Recht dieser Rechtsordnung geringstmögliche zulässige Maß beschränkt.\n\n🤝 FREISTELLUNG\nDer Nutzer verpflichtet sich, die Entwickler und Mitwirkenden von jeglichen Ansprüchen, Schäden, Verlusten oder Aufwendungen (einschließlich Anwaltskosten) freizustellen und schadlos zu halten, die sich aus der Nutzung der App durch den Nutzer oder aus einem Verstoß gegen diese Bedingungen ergeben.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ SALVATORISCHE KLAUSEL\nSollte sich eine Bestimmung dieser Bedingungen in einer bestimmten Rechtsordnung als nicht durchsetzbar erweisen, wird diese Bestimmung im geringstmöglichen erforderlichen Umfang eingeschränkt oder abgetrennt, während alle übrigen Bestimmungen in vollem Umfang wirksam bleiben.\n\nDurch die Nutzung von Roadstr, gleich an welchem Ort der Welt, bestätigt der Nutzer, sämtliche vorstehenden Bedingungen gelesen, verstanden und bedingungslos akzeptiert zu haben, und übernimmt die volle und vollständige Verantwortung — sowie das gesamte Risiko — für die Nutzung der Anwendung und jede sich daraus ergebende Folge.';

  @override
  String get readOnWikipedia => 'Auf Wikipedia lesen';

  @override
  String get openInBrowser => 'Im Browser öffnen';

  @override
  String get wikipediaLoadFailed =>
      'Wikipedia konnte nicht sicher geladen werden.';

  @override
  String get retry => 'Erneut versuchen';

  @override
  String get poiDetailsFromOsm => 'Informationen aus OpenStreetMap';

  @override
  String get poiCategory => 'Kategorie';

  @override
  String get poiOperator => 'Betreiber';

  @override
  String get poiCuisine => 'Küche';

  @override
  String get poiAccessibility => 'Barrierefreiheit';

  @override
  String get poiWheelchairYes => 'Rollstuhlgerecht';

  @override
  String get poiWheelchairLimited => 'Eingeschränkt rollstuhlgerecht';

  @override
  String get poiWheelchairNo => 'Nicht rollstuhlgerecht';

  @override
  String get poiContact => 'Kontakt';

  @override
  String get poiAddress => 'Adresse';

  @override
  String get poiWebsite => 'Webseite';

  @override
  String get poiAccessPrivate => 'Privater Zugang';

  @override
  String get poiAccessCustomers => 'Nur für Kunden';

  @override
  String get poiAccessPermit => 'Genehmigung erforderlich';

  @override
  String get poiAccessNo => 'Kein öffentlicher Zugang';

  @override
  String get poiAccessDestination => 'Zufahrt nur zum Ziel';

  @override
  String get poiLightningAccepted => 'Lightning wird akzeptiert';

  @override
  String get poiBitcoinAccepted => 'Bitcoin wird akzeptiert';

  @override
  String get poiParkingDetails => 'Parken';

  @override
  String get poiParkingSurface => 'Oberirdisch';

  @override
  String get poiParkingUnderground => 'Unterirdisch';

  @override
  String get poiParkingMultiStorey => 'Parkhaus';

  @override
  String get poiParkingStreetSide => 'Am Straßenrand';

  @override
  String get poiParkingLane => 'Auf der Straße';

  @override
  String get poiParkingRooftop => 'Dachparkplatz';

  @override
  String get poiFee => 'Gebühr';

  @override
  String get poiFree => 'Kostenlos';

  @override
  String get poiPaid => 'Kostenpflichtig';

  @override
  String get poiCapacity => 'Kapazität';

  @override
  String get poiMaxStay => 'Höchstparkdauer';

  @override
  String get poiPrice => 'Preis';

  @override
  String get poiChargingDetails => 'Laden';

  @override
  String get poiConnectorType2 => 'Typ 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Diesel';

  @override
  String get poiPetrol95 => 'Benzin 95';

  @override
  String get poiSmokingAllowed => 'Rauchen erlaubt';

  @override
  String get poiSmokingOutside => 'Rauchen im Außenbereich';

  @override
  String get poiSmokingAreas => 'Raucherbereiche';

  @override
  String get poiSmokeFree => 'Rauchfrei';

  @override
  String get poiOutdoorSeating => 'Außensitzplätze';

  @override
  String get poiTakeaway => 'Zum Mitnehmen';

  @override
  String get poiTakeawayOnly => 'Nur zum Mitnehmen';

  @override
  String get gpsSignalLost => 'GPS-Signal verloren';

  @override
  String get poiOpenNow => 'Jetzt geöffnet';

  @override
  String get poiClosedNow => 'Geschlossen';

  @override
  String poiOpensAt(String when) {
    return 'Öffnet: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Schließt: $when';
  }

  @override
  String searchOnEngine(String engine) {
    return 'Auf $engine suchen';
  }

  @override
  String get plannerFromHint => 'Von…';

  @override
  String get plannerToHint => 'Ziel…';

  @override
  String departEta(String dep, String arr) {
    return 'Abfahrt $dep  →  Ankunft $arr';
  }

  @override
  String get modeCar => 'Auto';

  @override
  String get modeBike => 'Fahrrad';

  @override
  String get modeWalk => 'Zu Fuß';

  @override
  String windSpeed(String speed) {
    return 'Wind $speed';
  }

  @override
  String durationMin(int m) {
    return '$m Min';
  }

  @override
  String durationHourMin(int h, int m) {
    return '${h}h ${m}min';
  }

  @override
  String get weatherClear => 'Klar';

  @override
  String get weatherPartlyCloudy => 'Teilw. bewölkt';

  @override
  String get weatherCloudy => 'Bewölkt';

  @override
  String get weatherFog => 'Nebel';

  @override
  String get weatherLightRain => 'Leichter Regen';

  @override
  String get weatherRain => 'Regen';

  @override
  String get weatherSnow => 'Schnee';

  @override
  String get weatherShowers => 'Schauer';

  @override
  String get weatherThunderstorm => 'Gewitter';

  @override
  String get ztlAheadWarning =>
      'Eingeschränkte Verkehrszone voraus — die Route führt hinein';

  @override
  String get ztlInsideWarning => 'Eingeschränkte Verkehrszone';

  @override
  String get onboardingAppSubtitle => 'Open-Source-Nostr-Navigation';

  @override
  String get onboardingWelcomeTitle => 'Willkommen';

  @override
  String get onboardingWelcomeBody =>
      'Alles, was eine Navigations-App braucht – ohne auf Ihre Privatsphäre zu verzichten.';

  @override
  String get onboardingFeatureNav => 'Turn-by-Turn-GPS-Navigation';

  @override
  String get onboardingFeatureNostr =>
      'Nostr-Straßenereignisse (Blitzer, Gefahren, Verkehr)';

  @override
  String get onboardingFeatureLightning =>
      'Lightning-Network-Trinkgelder für Melder';

  @override
  String get onboardingFeatureVoice =>
      'KI-Sprachführung auf dem Gerät (Kokoro-82M)';

  @override
  String get onboardingFeaturePrivacy =>
      'Kein Konto erforderlich — kein Tracking, nie';

  @override
  String get onboardingGetStarted => 'Loslegen';

  @override
  String get onboardingNostrTitle => 'Nostr-Identität';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — verbinden, um Straßenereignisse zu melden, Warnungen zu bestätigen und Lightning-Trinkgelder zu verdienen.';

  @override
  String get onboardingNostrConnected => 'Verbunden';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (empfohlen)';

  @override
  String get onboardingAmberSubtitle =>
      'Mit der Amber-Signer-App verbinden. Ihr privater Schlüssel verlässt Amber nie.';

  @override
  String get onboardingNsecTitle => 'nsec-Schlüssel';

  @override
  String get onboardingNsecSubtitle =>
      'Privaten Schlüssel einfügen. Im Android Keystore gespeichert.';

  @override
  String get onboardingNsecError =>
      'Ungültiger nsec-Schlüssel — bitte prüfen und erneut versuchen.';

  @override
  String get onboardingSkip => 'Vorerst überspringen';

  @override
  String get onboardingContinue => 'Weiter';

  @override
  String get onboardingEnterNsec => 'nsec-Schlüssel eingeben';

  @override
  String get onboardingSetupTitle => 'Roadstr einrichten';

  @override
  String get onboardingSetupSubtitle =>
      'Standortzugriff und optionale Sprachführung konfigurieren.';

  @override
  String get onboardingLocationTitle => 'Standort';

  @override
  String get onboardingLocationGranted => 'Standortzugriff gewährt';

  @override
  String get onboardingLocationRequired => 'Für GPS-Navigation erforderlich';

  @override
  String get onboardingGrantButton => 'Erlauben';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS-Nutzer';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

  @override
  String get onboardingVoiceTitle => 'KI-Sprachführung (optional)';

  @override
  String get onboardingVoiceReady => 'Kokoro-Sprachmodell bereit';

  @override
  String get onboardingVoiceDownloading => 'Sprachmodell wird heruntergeladen…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Das 82-MB-Kokoro-KI-Modell für geräteinterne Sprachführung herunterladen';

  @override
  String get onboardingVoiceChecking => 'Modellstatus wird überprüft…';

  @override
  String get onboardingDownloadButton => 'Herunterladen';

  @override
  String get onboardingVoiceLaterHint =>
      'Sie können das Sprachmodell auch später in\nEinstellungen → Navigationsstimme herunterladen.';

  @override
  String get onboardingReadyTitle => 'Sie können jetzt navigieren!';

  @override
  String get onboardingReadyBody =>
      'Roadstr öffnet jetzt die Karte.\nAlles andere können Sie in den Einstellungen konfigurieren.';

  @override
  String get onboardingLetsGo => 'Los geht\'s!';

  @override
  String get onboardingProfileLoading => 'Profil wird geladen…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Stimme';

  @override
  String get kokoroVoiceFemale => 'Weiblich';

  @override
  String get kokoroVoiceMale => 'Männlich';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Männliche Stimme für diese Sprache nicht verfügbar';

  @override
  String get kokoroSpeedTitle => 'Sprechgeschwindigkeit';

  @override
  String get kokoroVolumeTitle => 'Sprachlautstärke';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Optional: Deine gespeicherten Favoriten können über die Nostr-Relays zwischen deinen Geräten synchronisiert werden, Ende-zu-Ende-verschlüsselt (NIP-44) mit deinem eigenen Schlüssel — Relays sehen nur Chiffretext, und niemand außer dir kann die Inhalte lesen. Jederzeit in den Einstellungen aktivierbar.';

  @override
  String get parkingSaveHere => 'Parkposition hier speichern';

  @override
  String get parkingMarkerTitle => 'Parkplatz';

  @override
  String get parkingNavigateHere => 'Zum Parkplatz navigieren';

  @override
  String get parkingRemove => 'Parkposition entfernen';

  @override
  String get parkingSavedSnack => 'Parkposition gespeichert';

  @override
  String get parkingRemovedSnack => 'Parkposition entfernt';

  @override
  String get exportFavoritesTitle => 'Favoriten exportieren';

  @override
  String get exportFavoritesDesc =>
      'Speichere deine Lieblingsorte in einer JSON-Datei, die du sichern oder auf ein anderes Gerät übertragen kannst.';

  @override
  String get exportEncryptToggle => 'Mit Passwort verschlüsseln';

  @override
  String get exportPasswordHint => 'Passwort';

  @override
  String get exportButton => 'Exportieren';

  @override
  String get exportSuccessSnack => 'Favoriten exportiert';

  @override
  String get exportFailedSnack => 'Export fehlgeschlagen';

  @override
  String get importFavoritesTitle => 'Favoriten importieren';

  @override
  String get importPasswordPrompt =>
      'Diese Datei ist verschlüsselt — Passwort eingeben';

  @override
  String importSuccessSnack(int n) {
    return '$n Favoriten importiert';
  }

  @override
  String get importFailedSnack =>
      'Import fehlgeschlagen — Datei und Passwort prüfen';

  @override
  String get syncFavoritesTitle => 'Favoriten synchronisieren';

  @override
  String get syncFavoritesDesc =>
      'Veröffentliche deine Favoriten Ende-zu-Ende-verschlüsselt auf deinen Nostr-Relays, damit sie dir auf allen Geräten folgen. Relays sehen nur Chiffretext — niemand außer dir kann die Inhalte lesen.';

  @override
  String get syncNowButton => 'An Nostr senden';

  @override
  String get syncPullButton => 'Von Nostr abrufen';

  @override
  String get syncPassphraseTitle => 'Sync-Passphrase (optional)';

  @override
  String get syncPassphraseDesc =>
      'Zweite Verschlüsselungsebene für synchronisierte Favoriten: Selbst wenn Ihr Nostr-Schlüssel kompromittiert würde, bleibt der Snapshot auf den Relays ohne diese Passphrase unlesbar. Sie geben sie einmal pro neuem Gerät ein. Leer lassen zum Deaktivieren.';

  @override
  String get syncSuccessSnack => 'Favoriten synchronisiert';

  @override
  String get syncFailedSnack => 'Synchronisierung fehlgeschlagen';

  @override
  String syncLastSyncLabel(String when) {
    return 'Zuletzt synchronisiert: $when';
  }

  @override
  String get syncNoIdentity =>
      'Melde dich mit Nostr an (Einstellungen → Profil), um die Synchronisierung zu aktivieren';

  @override
  String get onboardingVpnNotice =>
      'Für maximale Privatsphäre — Meldungen werden über das Nostr-Netzwerk verbreitet — wird die Nutzung eines VPN empfohlen. Mullvad, bezahlbar mit Bitcoin, ist die empfohlene Wahl.';

  @override
  String get trafficNormal => 'Normaler Verkehr';

  @override
  String get trafficModerate => 'Mäßiger Verkehr';

  @override
  String get trafficHeavy => 'Dichter Verkehr';

  @override
  String get avoidHighwaysChip => 'Autobahnen meiden';

  @override
  String get avoidTollsChip => 'Maut vermeiden';

  @override
  String get preferShorterChip => 'Kürzeste Route';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Routenvorschau anzeigen';

  @override
  String get avoidHighwaysAndTolls => 'Autobahnen und Mautstraßen vermeiden';

  @override
  String get avoidRouteUnavailable =>
      'Es wurde keine Route gefunden, die Autobahnen und Mautstraßen gleichzeitig vermeidet.';

  @override
  String get avoidanceUnavoidableSection =>
      'Minimiert Autobahnen/Maut · unvermeidbarer Abschnitt';
}
