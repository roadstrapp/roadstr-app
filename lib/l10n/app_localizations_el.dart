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
      'Το Roadstr είναι μια πειραματική, ανοικτού κώδικα εφαρμογή πλοήγησης που συντηρείται από την κοινότητα, βασισμένη σε δεδομένα του OpenStreetMap και στο πρωτόκολλο Nostr, και διατίθεται προς χρήση σε οποιαδήποτε χώρα. Με τη λήψη, την εγκατάσταση ή τη χρήση αυτής της εφαρμογής, ο χρήστης αποδέχεται ανεπιφύλακτα όλους τους παρακάτω όρους, χωρίς εδαφικό περιορισμό.\n\n🚗 Η ΟΔΙΚΗ ΑΣΦΑΛΕΙΑ ΠΡΩΤΑ\nΟ οδηγός πρέπει πάντα να κρατά το βλέμμα του στον δρόμο και να συμμορφώνεται με όλους τους ισχύοντες κανόνες οδικής κυκλοφορίας και την τοποθετημένη σήμανση, τα οποία υπερισχύουν πάντοτε οποιασδήποτε οδηγίας της εφαρμογής. Μη χειρίζεστε ποτέ τη συσκευή κατά την οδήγηση· στερεώστε την σε εγκεκριμένη, ορατή βάση στήριξης πριν ξεκινήσετε, και μην αποσπάτε ποτέ την προσοχή σας από τον δρόμο για να αλληλεπιδράσετε με αυτήν ενώ το όχημα κινείται.\n\n⚠️ ΑΝΑΛΗΨΗ ΚΙΝΔΥΝΟΥ — ΠΑΓΚΟΣΜΙΩΣ\nΜε τη χρήση του Roadstr, σε οποιαδήποτε χώρα και υπό οποιοδήποτε νομικό σύστημα, ο χρήστης αναλαμβάνει εν γνώσει του και οικειοθελώς ΟΛΟΥΣ τους κινδύνους που συνδέονται με τη χρήση του, συμπεριλαμβανομένων, ενδεικτικά και όχι περιοριστικά: τροχαίων ατυχημάτων, σωματικής βλάβης, θανάτου, υλικών ζημιών, ζημιών στο όχημα, προστίμων, διοικητικών κυρώσεων, ρυμούλκησης, κατάσχεσης οχήματος, ποινικής ευθύνης ή οποιασδήποτε άλλης συνέπειας που προκύπτει άμεσα ή έμμεσα από την εξάρτηση από την εφαρμογή. Ο χρήστης φέρει αποκλειστικά την πλήρη ευθύνη για κάθε απόφαση οδήγησης και πλοήγησης.\n\n🚫 ΚΑΜΙΑ ΕΓΓΥΗΣΗ\nΤο Roadstr παρέχεται αυστηρά «ΩΣ ΕΧΕΙ» και «ΩΣ ΔΙΑΘΕΣΙΜΟ», χωρίς καμία εγγύηση οποιουδήποτε είδους, ρητή, σιωπηρή ή εκ του νόμου — συμπεριλαμβανομένων, ενδεικτικά, εγγυήσεων ακρίβειας, πληρότητας, αξιοπιστίας, διαθεσιμότητας, εμπορευσιμότητας, καταλληλότητας για συγκεκριμένο σκοπό και μη παραβίασης δικαιωμάτων τρίτων. Τα δεδομένα χαρτών, η δρομολόγηση, τα όρια ταχύτητας, οι κάμερες ταχύτητας και οι πληροφορίες για ζώνες περιορισμένης κυκλοφορίας (ZTL/ZAC/LTZ) προέρχονται από ανοικτές πηγές που συντηρούνται από την κοινότητα (OpenStreetMap, Overpass API), οι οποίες ενδέχεται να είναι ελλιπείς, ξεπερασμένες ή ανακριβείς για οποιαδήποτε χώρα, περιοχή ή δήμο, ανά πάσα στιγμή και χωρίς προειδοποίηση. Ο χρήστης είναι αποκλειστικά υπεύθυνος να επαληθεύει ανεξάρτητα, πριν και κατά τη διάρκεια του ταξιδιού, τη νομιμότητα και τη δυνατότητα πρόσβασης οποιασδήποτε προτεινόμενης διαδρομής βάσει της επίσημης τοπικής σήμανσης και νομοθεσίας.\n\n📍 ΑΚΡΙΒΕΙΑ & GPS\nΟ εντοπισμός θέσης μέσω GPS ενδέχεται να είναι ανακριβής ή μη διαθέσιμος. Όλες οι οδηγίες, οι αποστάσεις και οι ειδοποιήσεις παρέχονται αποκλειστικά ενδεικτικά και δεν πρέπει ποτέ να αποτελούν τη μοναδική βάση για μια απόφαση οδήγησης.\n\n🛡️ ΠΕΡΙΟΡΙΣΜΟΣ ΕΥΘΥΝΗΣ\nΣτον μέγιστο βαθμό που επιτρέπεται από την ισχύουσα νομοθεσία σε οποιαδήποτε δικαιοδοσία, οι προγραμματιστές, οι συντελεστές και κάθε μέρος που εμπλέκεται στη δημιουργία ή τη διανομή του Roadstr δεν φέρουν ευθύνη για οποιεσδήποτε άμεσες, έμμεσες, παρεπόμενες, επακόλουθες, ειδικές, παραδειγματικές ή τιμωρητικές ζημίες οποιουδήποτε είδους — συμπεριλαμβανομένων σωματικής βλάβης, θανάτου ή οικονομικής ζημίας — που προκύπτουν από ή σχετίζονται με τη χρήση ή την αδυναμία χρήσης της εφαρμογής, ακόμη και αν έχουν ενημερωθεί για την πιθανότητα τέτοιων ζημιών. Όπου μια δικαιοδοσία δεν επιτρέπει μέρος ή το σύνολο αυτού του περιορισμού, η ευθύνη περιορίζεται στον ελάχιστο βαθμό που επιτρέπεται από τον νόμο στην εν λόγω δικαιοδοσία.\n\n🤝 ΑΠΟΖΗΜΙΩΣΗ\nΟ χρήστης συμφωνεί να αποζημιώνει και να απαλλάσσει από κάθε ευθύνη τους προγραμματιστές και τους συντελεστές για κάθε αξίωση, ζημία, απώλεια ή δαπάνη (συμπεριλαμβανομένων δικηγορικών αμοιβών) που προκύπτει από τη χρήση της εφαρμογής από τον χρήστη ή από παραβίαση των παρόντων όρων.\n\n🔒 ΙΔΙΩΤΙΚΟΤΗΤΑ\nΚανένα δεδομένο θέσης δεν διαβιβάζεται στους δικούς του διακομιστές του Roadstr. Ο υπολογισμός της διαδρομής πραγματοποιείται μέσω υπηρεσιών τρίτων (OSRM, GraphHopper, OpenRouteService), στις οποίες αποστέλλονται μόνο οι συντεταγμένες αφετηρίας και προορισμού.\n\n⚖️ ΔΙΑΙΡΕΤΟΤΗΤΑ\nΕάν κάποια διάταξη των παρόντων όρων κριθεί ανεφάρμοστη σε μια δεδομένη δικαιοδοσία, η εν λόγω διάταξη θα περιορίζεται ή θα διαχωρίζεται στον ελάχιστο αναγκαίο βαθμό, και όλες οι υπόλοιπες διατάξεις θα παραμένουν σε πλήρη ισχύ.\n\nΧρησιμοποιώντας το Roadstr, οπουδήποτε στον κόσμο, ο χρήστης επιβεβαιώνει ότι έχει διαβάσει, κατανοήσει και αποδεχθεί ανεπιφύλακτα κάθε έναν από τους παραπάνω όρους, και αναλαμβάνει την πλήρη και ολοκληρωτική ευθύνη — και όλον τον κίνδυνο — για τη χρήση της εφαρμογής και κάθε συνέπεια που απορρέει από αυτήν.';

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
      'Μπροστά υπάρχει περιορισμένη κυκλοφοριακή ζώνη — η διαδρομή περνά μέσα από αυτήν';

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
  String get kokoroVoiceGenderTitle => 'Φωνή';

  @override
  String get kokoroVoiceFemale => 'Γυναικεία';

  @override
  String get kokoroVoiceMale => 'Ανδρική';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Η ανδρική φωνή δεν είναι διαθέσιμη για αυτήν τη γλώσσα';

  @override
  String get kokoroSpeedTitle => 'Ταχύτητα ομιλίας';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Προαιρετικό: τα αποθηκευμένα αγαπημένα σας μπορούν να συγχρονίζονται μεταξύ των συσκευών σας μέσω των Nostr relays, με κρυπτογράφηση από άκρο σε άκρο (NIP-44) με το δικό σας κλειδί — τα relays βλέπουν μόνο κρυπτογραφημένο κείμενο και κανείς εκτός από εσάς δεν μπορεί να διαβάσει το περιεχόμενο. Ενεργοποιήστε το οποτεδήποτε από τις Ρυθμίσεις.';

  @override
  String get parkingSaveHere => 'Αποθήκευση στάθμευσης εδώ';

  @override
  String get parkingMarkerTitle => 'Θέση στάθμευσης';

  @override
  String get parkingNavigateHere => 'Πλοήγηση στη στάθμευση';

  @override
  String get parkingRemove => 'Αφαίρεση στάθμευσης';

  @override
  String get parkingSavedSnack => 'Η θέση στάθμευσης αποθηκεύτηκε';

  @override
  String get parkingRemovedSnack => 'Η θέση στάθμευσης αφαιρέθηκε';

  @override
  String get exportFavoritesTitle => 'Εξαγωγή αγαπημένων';

  @override
  String get exportFavoritesDesc =>
      'Αποθηκεύστε τα αγαπημένα σας μέρη σε ένα αρχείο JSON που μπορείτε να κρατήσετε ως αντίγραφο ασφαλείας ή να μεταφέρετε σε άλλη συσκευή.';

  @override
  String get exportEncryptToggle => 'Κρυπτογράφηση με κωδικό';

  @override
  String get exportPasswordHint => 'Κωδικός πρόσβασης';

  @override
  String get exportButton => 'Εξαγωγή';

  @override
  String get exportSuccessSnack => 'Τα αγαπημένα εξήχθησαν';

  @override
  String get exportFailedSnack => 'Η εξαγωγή απέτυχε';

  @override
  String get importFavoritesTitle => 'Εισαγωγή αγαπημένων';

  @override
  String get importPasswordPrompt =>
      'Αυτό το αρχείο είναι κρυπτογραφημένο — εισαγάγετε τον κωδικό';

  @override
  String importSuccessSnack(int n) {
    return 'Εισήχθησαν $n αγαπημένα';
  }

  @override
  String get importFailedSnack =>
      'Η εισαγωγή απέτυχε — ελέγξτε το αρχείο και τον κωδικό';

  @override
  String get syncFavoritesTitle => 'Συγχρονισμός αγαπημένων';

  @override
  String get syncFavoritesDesc =>
      'Δημοσιεύστε τα αγαπημένα σας, κρυπτογραφημένα από άκρο σε άκρο, στα Nostr relays σας ώστε να σας ακολουθούν σε όλες τις συσκευές. Τα relays βλέπουν μόνο κρυπτογραφημένο κείμενο — κανείς εκτός από εσάς δεν μπορεί να διαβάσει το περιεχόμενο.';

  @override
  String get syncNowButton => 'Αποστολή στο Nostr';

  @override
  String get syncPullButton => 'Λήψη από το Nostr';

  @override
  String get syncSuccessSnack => 'Τα αγαπημένα συγχρονίστηκαν';

  @override
  String get syncFailedSnack => 'Ο συγχρονισμός απέτυχε';

  @override
  String syncLastSyncLabel(String when) {
    return 'Τελευταίος συγχρονισμός: $when';
  }

  @override
  String get syncNoIdentity =>
      'Συνδεθείτε με Nostr (Ρυθμίσεις → Προφίλ) για να ενεργοποιήσετε τον συγχρονισμό';

  @override
  String get onboardingVpnNotice =>
      'Για μέγιστη ιδιωτικότητα — οι αναφορές διαδίδονται στο δίκτυο Nostr — συνιστάται η χρήση VPN. Το Mullvad, με πληρωμή σε Bitcoin, είναι η προτεινόμενη επιλογή.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Για αξιόπιστη λειτουργία, ορίστε την άδεια τοποθεσίας της εφαρμογής σε «Να επιτρέπεται πάντα», όχι μόνο κατά τη χρήση.';

  @override
  String get trafficNormal => 'Κανονική κίνηση';

  @override
  String get trafficModerate => 'Μέτρια κίνηση';

  @override
  String get trafficHeavy => 'Έντονη κίνηση';

  @override
  String get avoidHighwaysChip => 'Αποφυγή αυτοκινητοδρόμων';

  @override
  String get avoidTollsChip => 'Αποφυγή διοδίων';

  @override
  String get preferShorterChip => 'Συντομότερη διαδρομή';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Προεπισκόπηση διαδρομής';
}
