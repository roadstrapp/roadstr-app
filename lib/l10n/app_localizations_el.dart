// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Modern Greek (`el`).
class AppLocalizationsEl extends AppLocalizations {
  AppLocalizationsEl([String locale = 'el']) : super(locale);

  @override
  String get searchHint => 'Πού θέλετε να πάτε;';

  @override
  String get calculatingRoute => 'Υπολογισμός διαδρομής…';

  @override
  String get routeNotFoundTitle => 'Η διαδρομή δεν βρέθηκε';

  @override
  String get noRouteFound => 'Δεν βρέθηκε διαδρομή. Ελέγξτε τη σύνδεσή σας.';

  @override
  String get emptyServerResponse =>
      'Κενή απόκριση διακομιστή. Ελέγξτε τη σύνδεσή σας.';

  @override
  String routeError(String error) {
    return 'Σφάλμα υπολογισμού διαδρομής: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS μη διαθέσιμο — Ρυθμίσεις → Εφαρμογή → Roadstr → Άδειες → Τοποθεσία';

  @override
  String get acquiringGps => 'Λήψη GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Ο διακομιστής GraphHopper δεν έχει ρυθμιστεί — χρησιμοποιείται OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'Το κλειδί API GraphHopper δεν έχει ρυθμιστεί — χρησιμοποιείται OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'Το κλειδί API OpenRouteService δεν έχει ρυθμιστεί — χρησιμοποιείται OSRM';

  @override
  String get chooseRoute => 'Επιλογή διαδρομής';

  @override
  String routeOptionsCount(int count) {
    return '$count επιλογές';
  }

  @override
  String get cancel => 'Ακύρωση';

  @override
  String get startNavigation => 'Εκκίνηση πλοήγησης';

  @override
  String get fastestRoute => 'Ταχύτερη';

  @override
  String get now => 'Τώρα';

  @override
  String get history => 'Ιστορικό';

  @override
  String get clearHistory => 'Εκκαθάριση';

  @override
  String get selectedPosition => 'Επιλεγμένη θέση';

  @override
  String get bottomBarProfile => 'Προφίλ';

  @override
  String get bottomBarMenu => 'Μενού';

  @override
  String get settingsTitle => 'Ρυθμίσεις';

  @override
  String get sectionTheme => 'Θέμα';

  @override
  String get sectionMap => 'Χάρτης';

  @override
  String get sectionInfo => 'Πληροφορίες';

  @override
  String get sectionLanguage => 'Γλώσσα';

  @override
  String get themeLightNostr => 'Φωτεινό · Nostr Βιολετί';

  @override
  String get themeLightBitcoin => 'Φωτεινό · Bitcoin Πορτοκαλί';

  @override
  String get themeDarkNostr => 'Σκούρο · Nostr Βιολετί';

  @override
  String get themeDarkBitcoin => 'Σκούρο · Bitcoin Πορτοκαλί';

  @override
  String get langSystem => 'Προεπιλογή συστήματος';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Διατήρηση οθόνης ενεργής';

  @override
  String get keepScreenOnDescription =>
      'Αποτρέπει την αδράνεια κατά την πλοήγηση';

  @override
  String get rotateMap => 'Ο χάρτης ακολουθεί την κατεύθυνση';

  @override
  String get rotateMapDescription =>
      'Ο χάρτης περιστρέφεται βάσει της κατεύθυνσης οδήγησης';

  @override
  String get mapTileUrlLabel => 'URL πλακιδίων χάρτη';

  @override
  String get routingProviderLabel => 'Πάροχος δρομολόγησης';

  @override
  String get osrmProvider => 'OSRM (δημόσιο, δεν απαιτείται κλειδί)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (τοπικό/ιδιωτικό)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (κλειδί API)';

  @override
  String get openrouteProvider => 'OpenRouteService (κλειδί API)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'Κλειδί API GraphHopper (προαιρετικό)';

  @override
  String get verify => 'Επαλήθευση';

  @override
  String get graphhopperServerUrlRequired =>
      'Εισάγετε τη διεύθυνση URL του διακομιστή πριν από την επαλήθευση.';

  @override
  String get successTitle => 'Επιτυχία';

  @override
  String get graphhopperServerReachable =>
      'Ο διακομιστής GraphHopper είναι προσβάσιμος';

  @override
  String get errorTitle => 'Σφάλμα';

  @override
  String get close => 'Κλείσιμο';

  @override
  String get infoVersion => 'Έκδοση';

  @override
  String get infoProtocol => 'Πρωτόκολλο';

  @override
  String get infoMaps => 'Χάρτες';

  @override
  String get infoRouting => 'Δρομολόγηση';

  @override
  String get infoSource => 'Πηγή';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted =>
      'GraphHopper (αυτο-φιλοξενούμενο)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Προφίλ';

  @override
  String get notConnected => 'Μη συνδεδεμένο';

  @override
  String get loginWithNostrTitle => 'ΣΥΝΔΕΣΗ ΜΕ NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Το ιδιωτικό κλειδί δεν φεύγει ποτέ από τη συσκευή σας · Συνιστάται';

  @override
  String get nsecLoginOption => 'Εισαγωγή nsec';

  @override
  String get nsecLoginDescription =>
      'Το ιδιωτικό κλειδί αποθηκεύεται τοπικά · Λιγότερο ασφαλές';

  @override
  String get connectedViaAmber => 'Συνδεδεμένο μέσω Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Συνδεδεμένο μέσω nsec';

  @override
  String get publicKeyLabel => 'ΔΗΜΟΣΙΟ ΚΛΕΙΔΙ';

  @override
  String get npubCopiedToClipboard => 'Το npub αντιγράφηκε στο πρόχειρο';

  @override
  String get logoutTitle => 'Αποσύνδεση';

  @override
  String get logoutConfirmation =>
      'Κατάργηση διαπιστευτηρίων Nostr από αυτή τη συσκευή;';

  @override
  String get logoutButton => 'Αποσύνδεση';

  @override
  String get nostrIdentityInfo =>
      'Με μια ταυτότητα Nostr μπορείτε να δημοσιεύετε κυκλοφοριακές ειδοποιήσεις, αναφορές και σημεία ενδιαφέροντος με αποκεντρωμένο τρόπο στο δίκτυο Nostr, χωρίς κεντρικούς διακομιστές.';

  @override
  String get warningTitle => 'Προειδοποίηση';

  @override
  String get nsecWarning =>
      'Πρόκειται να εισαγάγετε το ιδιωτικό κλειδί Nostr απευθείας σε μια εφαρμογή. Οποιοσδήποτε με φυσική ή απομακρυσμένη πρόσβαση στη συσκευή σας θα μπορούσε να το διαβάσει και να αναλάβει μόνιμα τον έλεγχο της Nostr ταυτότητάς σας.';

  @override
  String get amberSecureMethodHint =>
      '✦  Η ασφαλής μέθοδος είναι το Amber (NIP-55): το nsec δεν φεύγει ποτέ από το θησαυροφυλάκιο του υπογράφοντος της εφαρμογής.';

  @override
  String get nsecRiskAcknowledgment =>
      'Κατανοώ τον κίνδυνο και θέλω να συνεχίσω ούτως ή άλλως';

  @override
  String get continueButton => 'Συνέχεια';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Το Amber είναι ένας υπογράφων εφαρμογών Android συμβατός με NIP-55. Το ιδιωτικό κλειδί σας παραμένει απομονωμένο στο Amber και δεν κοινοποιείται ποτέ.';

  @override
  String get requestKeyFromAmber => 'Αίτηση δημόσιου κλειδιού από το Amber';

  @override
  String get amberNotFound =>
      'Το Amber δεν βρέθηκε. Εγκαταστήστε το από το Play Store ή εισαγάγετε χειροκίνητα το npub σας.';

  @override
  String get waitingForAmberResponse => 'Αναμονή απόκρισης Amber…';

  @override
  String get pasteNpubManually => 'Επικολλήστε το npub σας χειροκίνητα:';

  @override
  String get confirmNpub => 'Επιβεβαίωση npub';

  @override
  String get enterNsecTitle => 'Εισαγωγή nsec';

  @override
  String get loginButton => 'Σύνδεση';

  @override
  String get invalidNpubTitle => 'Μη έγκυρο npub';

  @override
  String get invalidNsecTitle => 'Μη έγκυρο nsec';

  @override
  String get invalidNpubMessage =>
      'Βεβαιωθείτε ότι επικολλήσατε το σωστό npub.';

  @override
  String get invalidNsecMessage =>
      'Βεβαιωθείτε ότι επικολλήσατε το σωστό nsec.';

  @override
  String get amberResponseError => 'Σφάλμα απόκρισης Amber';

  @override
  String get ok => 'OK';

  @override
  String get or => 'ή';

  @override
  String get gpsNotActiveTitle => 'Το GPS δεν είναι ενεργό';

  @override
  String get gpsDisabledMessage =>
      'Ενεργοποιήστε το GPS στις ρυθμίσεις της συσκευής σας για να λάβετε την τοποθεσία σας και να χρησιμοποιήσετε την πλοήγηση.';

  @override
  String get openSettings => 'Ρυθμίσεις';

  @override
  String get myLocation => 'Η τοποθεσία μου';

  @override
  String get loginToReport =>
      'Συνδεθείτε με Nostr (ενότητα Προφίλ) για αναφορά συμβάντων';

  @override
  String get navigateHere => 'Πλοήγηση εδώ';

  @override
  String get reportEventHere => 'Αναφορά συμβάντος εδώ';

  @override
  String get zapSendSats => 'Zap ⚡ (αποστολή sats)';

  @override
  String get sendZap => 'Αποστολή Zap';

  @override
  String get chooseAmountSats => 'Επιλέξτε ποσό σε satoshi (sats):';

  @override
  String get customAmount => 'Προσαρμοσμένο ποσό…';

  @override
  String get zapSending => 'Αποστολή…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Λήψη διεύθυνσης Lightning…';

  @override
  String get noLightningAddress =>
      'Αυτός ο αναφέρων δεν έχει διεύθυνση Lightning';

  @override
  String get requestingInvoice => 'Αίτηση τιμολογίου…';

  @override
  String get lnurlUnavailable => 'LNURL μη διαθέσιμο';

  @override
  String get invoiceFailed => 'Αδύνατη η δημιουργία τιμολογίου';

  @override
  String get openingWallet => 'Άνοιγμα πορτοφολιού…';

  @override
  String get payingViaNwc => 'Πληρωμή μέσω NWC…';

  @override
  String get noLightningWallet => 'Δεν βρέθηκε πορτοφόλι Lightning';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats στάλθηκαν!';
  }

  @override
  String get stillThere => 'Υπάρχει ακόμα';

  @override
  String get notThereAnymore => 'Δεν υπάρχει πλέον';

  @override
  String get loginToConfirm =>
      'Συνδεθείτε με Nostr για επιβεβαίωση ή αμφισβήτηση';

  @override
  String get reportAnEvent => 'Αναφορά συμβάντος';

  @override
  String get optionalComment => 'Προαιρετικό σχόλιο…';

  @override
  String get publish => 'Δημοσίευση';

  @override
  String get publishing => 'Δημοσίευση…';

  @override
  String get reportPublished => 'Αναφορά δημοσιεύτηκε ✓';

  @override
  String get myReports => 'ΟΙ ΑΝΑΦΟΡΕΣ ΜΟΥ';

  @override
  String get noReportsYet => 'Δεν έχουν δημοσιευτεί αναφορές';

  @override
  String get zapBalance => 'Υπόλοιπο Zap';

  @override
  String get satoshiFromReports => 'Satoshi που ελήφθησαν από τις αναφορές σας';

  @override
  String get reputationHigh => 'Υψηλή';

  @override
  String get reputationMedium => 'Μέτρια';

  @override
  String get reputationLow => 'Χαμηλή';

  @override
  String reputationLabel(String level) {
    return 'Φήμη $level';
  }

  @override
  String reliability(int pct) {
    return 'Αξιοπιστία: $pct%';
  }

  @override
  String get confirmedLabel => 'Επιβεβαιωμένο';

  @override
  String get removedLabel => 'Αφαιρέθηκε';

  @override
  String get positionLabel => 'Θέση';

  @override
  String get loadingLabel => 'Φόρτωση…';

  @override
  String get sectionWebSearch => 'Αναζήτηση ιστού';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Επικολλήστε το NWC URI σας (Alby Hub, Mutiny, Cashu…) για πληρωμή Zaps απευθείας από την εφαρμογή.';

  @override
  String get searchEngineQwantDesc =>
      'Ευρωπαϊκή, με προτεραιότητα στο απόρρητο. Χωρίς παρακολούθηση, χωρίς διαφημιστικά προφίλ. Συνιστάται.';

  @override
  String get searchEngineBraveDesc =>
      'Ανεξάρτητος δείκτης, ανοιχτού κώδικα. Χωρίς εξάρτηση από Google ή Bing. Μηδενική κατάρτιση προφίλ.';

  @override
  String get searchEngineDdgDesc =>
      'Εστιασμένη στο απόρρητο και δημοφιλής. Αποτελέσματα εν μέρει από Bing — λάβετε αυτό υπόψη.';

  @override
  String get searchEngineStartpageDesc =>
      'Αποτελέσματα Google χωρίς να παραδίδετε τα δεδομένα σας στην Google. Μια λογική συμβιβαστική λύση.';

  @override
  String get searchEngineGoogleDesc =>
      'Πολύ αποτελεσματική. Αλλά θυμηθείτε: η Google σας ξέρει καλύτερα από τη μαμά σας και πουλάει τα πάντα σε διαφημιστές. Η επιλογή σας. 🍪';

  @override
  String get categoryPolice => 'Αστυνομία';

  @override
  String get categorySpeedCamera => 'Κάμερα ταχύτητας';

  @override
  String get categoryTrafficJam => 'Κυκλοφοριακή συμφόρηση';

  @override
  String get categoryAccident => 'Τροχαίο ατύχημα';

  @override
  String get categoryRoadClosure => 'Κλειστός δρόμος';

  @override
  String get categoryConstruction => 'Εργοτάξιο';

  @override
  String get categoryHazard => 'Κίνδυνος';

  @override
  String get categoryRoadCondition => 'Κατάσταση οδοστρώματος';

  @override
  String get categoryPothole => 'Λακκούβα';

  @override
  String get categoryFog => 'Ομίχλη';

  @override
  String get categoryIce => 'Πάγος';

  @override
  String get categoryAnimal => 'Ζώο';

  @override
  String get categoryOther => 'Άλλο';

  @override
  String get dateTimeLabel => 'Ημερομηνία / ώρα';

  @override
  String minutesAgo(int count) {
    return 'πριν $count λεπτά';
  }

  @override
  String hoursAgo(int count) {
    return 'πριν $countω';
  }

  @override
  String daysAgo(int count) {
    return 'πριν $countη';
  }

  @override
  String get sectionFavorites => 'Αποθηκευμένα μέρη';

  @override
  String get addFavorite => 'Προσθήκη μέρους';

  @override
  String get favoriteLabelHint => 'Όνομα (π.χ. Σπίτι, Γραφείο)';

  @override
  String get favoriteAddressHint => 'Διεύθυνση';

  @override
  String get favoriteGeocodingError =>
      'Η διεύθυνση δεν βρέθηκε. Δοκίμασε πιο συγκεκριμένη.';

  @override
  String get trafficAlertTitle => 'Νέα κίνηση στη διαδρομή';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category αναφέρθηκε $age στη διαδρομή σας.\n\nΘέλετε να βρείτε εναλλακτική διαδρομή;';
  }

  @override
  String get trafficContinue => 'Συνέχεια';

  @override
  String get trafficRecalculate => 'Επανυπολογισμός διαδρομής';

  @override
  String get navExitTitle => 'Έξοδος από πλοήγηση;';

  @override
  String get navExitBody =>
      'Θέλετε να σταματήσετε την πλοήγηση και να επιστρέψετε στον χάρτη;';

  @override
  String get navContinue => 'Συνέχεια πλοήγησης';

  @override
  String get navExit => 'Ναι, έξοδος';

  @override
  String get loadingInfo => 'Φόρτωση πληροφοριών…';

  @override
  String get conditionsOnRoute => 'Συνθήκες στη διαδρομή';

  @override
  String get calculateRoute => 'Υπολογισμός διαδρομής';

  @override
  String get sectionNavigationVoice => 'Φωνή πλοήγησης';

  @override
  String get voiceGuidance => 'Φωνητική καθοδήγηση';

  @override
  String get voiceGuidanceDesc =>
      'Ανάγνωση οδηγιών στροφής δυνατά κατά την πλοήγηση';

  @override
  String get testVoiceEngine => 'Δοκιμή μηχανής φωνής';

  @override
  String get testVoiceEngineDesc =>
      'Πατήστε για έλεγχο της μηχανής TTS και οδηγίες ρύθμισης';

  @override
  String get ttsDialogTitle => 'Λείπει μηχανή φωνής';

  @override
  String get ttsDialogBody =>
      'Δεν βρέθηκε λειτουργική μηχανή Text-to-Speech.\n\nΤο Roadstr βασίζεται αποκλειστικά σε λογισμικό ανοιχτού κώδικα — εγκαταστήστε μία από αυτές τις δωρεάν μηχανές από το F-Droid:';

  @override
  String get ttsRhvoiceDesc =>
      'Φυσικά ηχητική φωνή, περιορισμένη λίστα γλωσσών';

  @override
  String get ttsEspeakDesc => 'Καλύπτει πάνω από 100 γλώσσες, ρομποτική φωνή';

  @override
  String get ttsInstallNote =>
      '⚠️ Μετά την εγκατάσταση:\n1. Ρυθμίσεις Android → Προσβασιμότητα → Σύνθεση φωνής\n2. Επιλέξτε τη μηχανή που μόλις εγκαταστήσατε\n3. Κατεβάστε τα φωνητικά δεδομένα της γλώσσας σας\n4. Επανεκκινήστε πλήρως το Roadstr';

  @override
  String get ttsTestNow => 'Δοκιμή τώρα';

  @override
  String get voiceUnsupportedTitle =>
      'Η φωνητική καθοδήγηση δεν είναι διαθέσιμη';

  @override
  String get voiceUnsupportedBody =>
      'Η γλώσσα σας δεν υποστηρίζεται ακόμα για φωνητικές οδηγίες στροφής. Οι οδηγίες πλοήγησης θα συνεχίσουν να εμφανίζονται ως κείμενο στην οθόνη.';

  @override
  String get kokoroModelTitle => 'Φωνητικό μοντέλο (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Δεν έχει ληφθεί · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Λήψη...';

  @override
  String get kokoroModelStatusReady => 'Το φωνητικό μοντέλο είναι έτοιμο';

  @override
  String get kokoroModelDownloadBtn => 'Λήψη';

  @override
  String get kokoroModelSupportedLangs =>
      'Υποστηρίζει: ιταλικά, αγγλικά, ισπανικά, γαλλικά, ιαπωνικά, κινεζικά, πορτογαλικά';

  @override
  String get autoDarkMode => 'Αυτόματο σκούρο θέμα';

  @override
  String get autoDarkModeDesc => 'Ενεργοποιεί το σκούρο θέμα στο ηλιοβασίλεμα';

  @override
  String get settingsImperialUnits => 'Αγγλοσαξονικές μονάδες';

  @override
  String get settingsImperialUnitsDesc =>
      'Μίλια και πόδια αντί για χιλιόμετρα και μέτρα';

  @override
  String get arrivedTitle => '🎉 Φτάσατε!';

  @override
  String get arrivedBody => 'Φτάσατε στον προορισμό σας.';

  @override
  String get arrivedFeedbackPrompt => 'Πώς πήγε;';

  @override
  String get feedbackBad => 'Κακά';

  @override
  String get feedbackGood => 'Καλά!';

  @override
  String get feedbackDialogTitle => 'Πείτε μας τι πήγε στραβά';

  @override
  String get feedbackHint => 'Περιγράψτε το πρόβλημα…';

  @override
  String get feedbackSent => 'Ανατροφοδότηση στάλθηκε — ευχαριστώ! 🙏';

  @override
  String get feedbackSubmit => 'Αποστολή';

  @override
  String get transportModeCar => 'Αυτοκίνητο';

  @override
  String get transportModeWalk => 'Με τα πόδια';

  @override
  String etaArrivalLabel(String time) {
    return 'Άφ. $time';
  }

  @override
  String get supportRoadstr => 'Υποστήριξε το Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address αντιγράφηκε στο πρόχειρο';
  }

  @override
  String get disclaimerTitle => 'Σημαντική ειδοποίηση';

  @override
  String get disclaimerAccept => 'Διάβασα και αποδέχομαι';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Διαβάστε στη Wikipedia';

  @override
  String searchOnEngine(String engine) {
    return 'Αναζήτηση στο $engine';
  }

  @override
  String get plannerFromHint => 'Από…';

  @override
  String get plannerToHint => 'Προορισμός…';

  @override
  String departEta(String dep, String arr) {
    return 'Αναχώρηση $dep  →  Άφιξη $arr';
  }

  @override
  String get modeCar => 'Αυτοκίνητο';

  @override
  String get modeBike => 'Ποδήλατο';

  @override
  String get modeWalk => 'Πεζός';

  @override
  String windSpeed(String speed) {
    return 'άνεμος $speed km/h';
  }

  @override
  String durationMin(int m) {
    return '$m λεπτ';
  }

  @override
  String durationHourMin(int h, int m) {
    return '$hω $mλεπτ';
  }

  @override
  String get weatherClear => 'Αίθριος';

  @override
  String get weatherPartlyCloudy => 'Αραιές νεφώσεις';

  @override
  String get weatherCloudy => 'Συννεφιά';

  @override
  String get weatherFog => 'Ομίχλη';

  @override
  String get weatherLightRain => 'Ψιχάλα';

  @override
  String get weatherRain => 'Βροχή';

  @override
  String get weatherSnow => 'Χιόνι';

  @override
  String get weatherShowers => 'Καταιγίδα';

  @override
  String get weatherThunderstorm => 'Καταιγίδα';

  @override
  String get ztlAheadWarning =>
      'Ζώνη ZTL μπροστά — η διαδρομή εισέρχεται σε περιορισμένη ζώνη';

  @override
  String get ztlInsideWarning => 'Περιορισμένη κυκλοφοριακή ζώνη';

  @override
  String get onboardingAppSubtitle => 'Πλοήγηση Nostr ανοιχτού κώδικα';

  @override
  String get onboardingWelcomeTitle => 'Καλώς ορίσατε';

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
  String get onboardingGetStarted => 'Ξεκινήστε';

  @override
  String get onboardingNostrTitle => 'Ταυτότητα Nostr';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Συνδεδεμένο';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (συνιστάται)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'Κλειδί nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Μη έγκυρο κλειδί nsec — ελέγξτε και δοκιμάστε ξανά.';

  @override
  String get onboardingSkip => 'Παράλειψη προς το παρόν';

  @override
  String get onboardingContinue => 'Συνέχεια';

  @override
  String get onboardingEnterNsec => 'Εισαγάγετε το κλειδί nsec';

  @override
  String get onboardingSetupTitle => 'Ρύθμιση Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Τοποθεσία';

  @override
  String get onboardingLocationGranted => 'Χορηγήθηκε πρόσβαση τοποθεσίας';

  @override
  String get onboardingLocationRequired => 'Απαιτείται για πλοήγηση GPS';

  @override
  String get onboardingGrantButton => 'Χορήγηση';

  @override
  String get onboardingGrapheneTitle => 'Χρήστες GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'Φωνητική καθοδήγηση ΤΝ (προαιρετικό)';

  @override
  String get onboardingVoiceReady => 'Το μοντέλο φωνής Kokoro είναι έτοιμο';

  @override
  String get onboardingVoiceDownloading => 'Λήψη μοντέλου φωνής…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Λήψη';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Είστε έτοιμοι να πλοηγηθείτε!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Πάμε!';

  @override
  String get onboardingProfileLoading => 'Φόρτωση προφίλ…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Voice';

  @override
  String get kokoroVoiceFemale => 'Female';

  @override
  String get kokoroVoiceMale => 'Male';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Male voice not available for this language';

  @override
  String get kokoroSpeedTitle => 'Speech speed';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Optional: your saved favorites can sync across your devices through the Nostr relays, end-to-end encrypted (NIP-44) with your own key — relays only ever see ciphertext, and nobody but you can read the contents. Enable it anytime from Settings.';

  @override
  String get parkingSaveHere => 'Save parking here';

  @override
  String get parkingMarkerTitle => 'Parking spot';

  @override
  String get parkingNavigateHere => 'Navigate to parking';

  @override
  String get parkingRemove => 'Remove parking';

  @override
  String get parkingSavedSnack => 'Parking spot saved';

  @override
  String get parkingRemovedSnack => 'Parking spot removed';

  @override
  String get exportFavoritesTitle => 'Export favorites';

  @override
  String get exportFavoritesDesc =>
      'Save your favorite places to a JSON file you can back up or move to another device.';

  @override
  String get exportEncryptToggle => 'Encrypt with a password';

  @override
  String get exportPasswordHint => 'Password';

  @override
  String get exportButton => 'Export';

  @override
  String get exportSuccessSnack => 'Favorites exported';

  @override
  String get exportFailedSnack => 'Export failed';

  @override
  String get importFavoritesTitle => 'Import favorites';

  @override
  String get importButton => 'Import';

  @override
  String get importPasswordPrompt =>
      'This file is encrypted — enter the password';

  @override
  String importSuccessSnack(int n) {
    return '$n favorites imported';
  }

  @override
  String get importFailedSnack => 'Import failed — check the file and password';

  @override
  String get syncFavoritesTitle => 'Sync favorites';

  @override
  String get syncFavoritesDesc =>
      'Publish your favorites, end-to-end encrypted, to your Nostr relays so they follow you across devices. Relays only ever see ciphertext — nobody but you can read the contents.';

  @override
  String get syncFavoritesEnable => 'Enable sync';

  @override
  String get syncNowButton => 'Push to Nostr';

  @override
  String get syncPullButton => 'Pull from Nostr';

  @override
  String get syncPushingStatus => 'Publishing…';

  @override
  String get syncPullingStatus => 'Fetching…';

  @override
  String get syncSuccessSnack => 'Favorites synced';

  @override
  String get syncFailedSnack => 'Sync failed';

  @override
  String get syncNotAvailableAmber =>
      'Encrypted sync isn\'t available with Amber sign-in yet';

  @override
  String syncLastSyncLabel(String when) {
    return 'Last synced: $when';
  }

  @override
  String get syncNoIdentity =>
      'Sign in with Nostr (Settings → Profile) to enable sync';

  @override
  String get syncPullConfirmTitle => 'Replace local favorites?';

  @override
  String get syncPullConfirmBody =>
      'This will merge favorites fetched from Nostr with the ones already on this device.';

  @override
  String get onboardingVpnNotice =>
      'Για μέγιστη ιδιωτικότητα — οι αναφορές διαδίδονται στο δίκτυο Nostr — συνιστάται η χρήση VPN. Το Mullvad, με πληρωμή σε Bitcoin, είναι η προτεινόμενη επιλογή.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Για αξιόπιστη λειτουργία, ορίστε την άδεια τοποθεσίας της εφαρμογής σε «Να επιτρέπεται πάντα», όχι μόνο κατά τη χρήση.';
}
