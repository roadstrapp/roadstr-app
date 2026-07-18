// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get searchHint => 'Where do you want to go?';

  @override
  String get calculatingRoute => 'Calculating route…';

  @override
  String get routeNotFoundTitle => 'Route not found';

  @override
  String get noRouteFound => 'No route found. Check your connection.';

  @override
  String get emptyServerResponse =>
      'Empty server response. Check your connection.';

  @override
  String routeError(String error) {
    return 'Route calculation error: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS unavailable — Settings → App → Roadstr → Permissions → Location';

  @override
  String get acquiringGps => 'Acquiring GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopper server not configured — using OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper API key not configured — using OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService API key not configured — using OSRM';

  @override
  String get chooseRoute => 'Choose route';

  @override
  String routeOptionsCount(int count) {
    return '$count options';
  }

  @override
  String get cancel => 'Cancel';

  @override
  String get startNavigation => 'Start navigation';

  @override
  String get fastestRoute => 'Fastest';

  @override
  String get now => 'Now';

  @override
  String get history => 'History';

  @override
  String get clearHistory => 'Clear';

  @override
  String get selectedPosition => 'Selected position';

  @override
  String get bottomBarProfile => 'Profile';

  @override
  String get bottomBarMenu => 'Menu';

  @override
  String get settingsTitle => 'Settings';

  @override
  String get sectionTheme => 'Theme';

  @override
  String get sectionMap => 'Map';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Language';

  @override
  String get themeLightNostr => 'Light · Nostr Violet';

  @override
  String get themeLightBitcoin => 'Light · Bitcoin Orange';

  @override
  String get themeDarkNostr => 'Dark · Nostr Violet';

  @override
  String get themeDarkBitcoin => 'Dark · Bitcoin Orange';

  @override
  String get langSystem => 'System default';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Keep screen on';

  @override
  String get keepScreenOnDescription => 'Prevents sleep during navigation';

  @override
  String get autoCenterOnLaunch => 'Center on my position at startup';

  @override
  String get autoCenterOnLaunchDesc =>
      'Uses GPS automatically only after location permission has already been granted';

  @override
  String get rotateMap => 'Map follows direction';

  @override
  String get rotateMapDescription => 'Map rotates based on driving direction';

  @override
  String get mapTileUrlLabel => 'Map tile URL';

  @override
  String get routingProviderLabel => 'Routing provider';

  @override
  String get osrmProvider => 'OSRM (public, no key required)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (local/private)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API key)';

  @override
  String get openrouteProvider => 'OpenRouteService (API key)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API key (optional)';

  @override
  String get verify => 'Verify';

  @override
  String get graphhopperServerUrlRequired =>
      'Enter the server URL before verifying.';

  @override
  String get successTitle => 'Success';

  @override
  String get graphhopperServerReachable => 'GraphHopper server reachable';

  @override
  String get errorTitle => 'Error';

  @override
  String get close => 'Close';

  @override
  String get infoVersion => 'Version';

  @override
  String get infoProtocol => 'Protocol';

  @override
  String get infoMaps => 'Maps';

  @override
  String get infoRouting => 'Routing';

  @override
  String get infoSource => 'Source';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (self-hosted)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (cloud)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profile';

  @override
  String get notConnected => 'Not connected';

  @override
  String get loginWithNostrTitle => 'LOGIN WITH NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Private key never leaves your device · Recommended';

  @override
  String get nsecLoginOption => 'Insert nsec';

  @override
  String get nsecLoginDescription => 'Private key stored locally · Less secure';

  @override
  String get connectedViaAmber => 'Connected via Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Connected via nsec';

  @override
  String get publicKeyLabel => 'PUBLIC KEY';

  @override
  String get npubCopiedToClipboard => 'npub copied to clipboard';

  @override
  String get logoutTitle => 'Disconnect';

  @override
  String get logoutConfirmation => 'Remove Nostr credentials from this device?';

  @override
  String get logoutButton => 'Disconnect';

  @override
  String get nostrIdentityInfo =>
      'With a Nostr identity you can publish traffic alerts, reports and points of interest in a decentralized way on the Nostr network, without central servers.';

  @override
  String get warningTitle => 'Warning';

  @override
  String get nsecWarning =>
      'You are about to rawdog your Nostr private key directly into an app. Anyone with physical or remote access to your device could read it and permanently take control of your Nostr identity.';

  @override
  String get amberSecureMethodHint =>
      '✦  The safe method is Amber (NIP-55): the nsec never leaves the app signer vault.';

  @override
  String get nsecRiskAcknowledgment =>
      'I understand the risk and want to continue anyway';

  @override
  String get continueButton => 'Continue';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber is an Android NIP-55 compliant app signer. Your private key stays isolated inside Amber and is never shared.';

  @override
  String get requestKeyFromAmber => 'Request public key from Amber';

  @override
  String get amberNotFound =>
      'Amber not found. Install it from the Play Store or enter your npub manually.';

  @override
  String get waitingForAmberResponse => 'Waiting for Amber response…';

  @override
  String get pasteNpubManually => 'Paste your npub manually:';

  @override
  String get confirmNpub => 'Confirm npub';

  @override
  String get enterNsecTitle => 'Insert nsec';

  @override
  String get loginButton => 'Login';

  @override
  String get invalidNpubTitle => 'Invalid npub';

  @override
  String get invalidNsecTitle => 'Invalid nsec';

  @override
  String get invalidNpubMessage => 'Make sure you pasted the correct npub.';

  @override
  String get invalidNsecMessage => 'Make sure you pasted the correct nsec.';

  @override
  String get amberResponseError => 'Amber response error';

  @override
  String get ok => 'OK';

  @override
  String get or => 'or';

  @override
  String get gpsNotActiveTitle => 'GPS not active';

  @override
  String get gpsDisabledMessage =>
      'Activate GPS in your device settings to get your location and use navigation.';

  @override
  String get openSettings => 'Settings';

  @override
  String get myLocation => 'My location';

  @override
  String get loginToReport =>
      'Login with Nostr (Profile section) to report events';

  @override
  String get navigateHere => 'Navigate here';

  @override
  String get reportEventHere => 'Report event here';

  @override
  String get zapSendSats => 'Zap ⚡ (send sats)';

  @override
  String get sendZap => 'Send a Zap';

  @override
  String get chooseAmountSats => 'Choose amount in satoshi (sats):';

  @override
  String get customAmount => 'Custom amount…';

  @override
  String get zapSending => 'Sending…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Fetching Lightning address…';

  @override
  String get noLightningAddress => 'This reporter has no Lightning address';

  @override
  String get requestingInvoice => 'Requesting invoice…';

  @override
  String get lnurlUnavailable => 'LNURL not available';

  @override
  String get invoiceFailed => 'Unable to generate invoice';

  @override
  String get openingWallet => 'Opening wallet…';

  @override
  String get payingViaNwc => 'Paying via NWC…';

  @override
  String get noLightningWallet => 'No Lightning wallet found';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats sent!';
  }

  @override
  String get stillThere => 'Still there';

  @override
  String get notThereAnymore => 'Not there anymore';

  @override
  String get loginToConfirm => 'Login with Nostr to confirm or dispute';

  @override
  String get reportAnEvent => 'Report an event';

  @override
  String get optionalComment => 'Optional comment…';

  @override
  String get publish => 'Publish';

  @override
  String get publishing => 'Publishing…';

  @override
  String get reportPublished => 'Report published ✓';

  @override
  String get myReports => 'MY REPORTS';

  @override
  String get noReportsYet => 'No reports published';

  @override
  String get zapBalance => 'Zap Balance';

  @override
  String get satoshiFromReports => 'Satoshi received from your reports';

  @override
  String get reputationHigh => 'High';

  @override
  String get reputationMedium => 'Medium';

  @override
  String get reputationLow => 'Low';

  @override
  String reputationLabel(String level) {
    return 'Reputation $level';
  }

  @override
  String reliability(int pct) {
    return 'Reliability: $pct%';
  }

  @override
  String get confirmedLabel => 'Confirmed';

  @override
  String get removedLabel => 'Removed';

  @override
  String get positionLabel => 'Position';

  @override
  String get loadingLabel => 'Loading…';

  @override
  String get sectionWebSearch => 'Web Search';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Paste your NWC URI (Alby Hub, Mutiny, Cashu…) to pay Zaps directly from the app.';

  @override
  String get searchEngineQwantDesc =>
      'European, privacy-first. No tracking, no ad profiles. Recommended.';

  @override
  String get searchEngineBraveDesc =>
      'Independent index, open-source. No Google or Bing dependency. Zero profiling.';

  @override
  String get searchEngineDdgDesc =>
      'Privacy-focused and popular. Results partially from Bing — keep that in mind.';

  @override
  String get searchEngineStartpageDesc =>
      'Google results without handing your data to Google. A reasonable compromise.';

  @override
  String get searchEngineGoogleDesc =>
      'Very effective. But remember: Google knows you better than your mother and sells everything to advertisers. Your call though. 🍪';

  @override
  String get categoryPolice => 'Police';

  @override
  String get categorySpeedCamera => 'Speed Camera';

  @override
  String get categoryTrafficJam => 'Traffic Jam';

  @override
  String get categoryAccident => 'Accident';

  @override
  String get categoryRoadClosure => 'Road Closure';

  @override
  String get categoryConstruction => 'Construction';

  @override
  String get categoryHazard => 'Hazard';

  @override
  String get categoryRoadCondition => 'Road Condition';

  @override
  String get categoryPothole => 'Pothole';

  @override
  String get categoryFog => 'Fog';

  @override
  String get categoryIce => 'Ice';

  @override
  String get categoryAnimal => 'Animal';

  @override
  String get categoryOther => 'Other';

  @override
  String get dateTimeLabel => 'Date / time';

  @override
  String minutesAgo(int count) {
    return '$count min ago';
  }

  @override
  String hoursAgo(int count) {
    return '${count}h ago';
  }

  @override
  String daysAgo(int count) {
    return '${count}d ago';
  }

  @override
  String get sectionFavorites => 'Saved Places';

  @override
  String get addFavorite => 'Add place';

  @override
  String get favoriteLabelHint => 'Name (e.g. Home, Office)';

  @override
  String get favoriteAddressHint => 'Address';

  @override
  String get favoriteGeocodingError =>
      'Address not found. Try a more specific address.';

  @override
  String get trafficAlertTitle => 'New traffic on route';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category reported $age on your route.\n\nDo you want to find an alternative route?';
  }

  @override
  String get trafficContinue => 'Continue';

  @override
  String get trafficRecalculate => 'Recalculate route';

  @override
  String get navExitTitle => 'Exit navigation?';

  @override
  String get navExitBody =>
      'Do you want to stop navigation and return to the map?';

  @override
  String get navContinue => 'Continue navigation';

  @override
  String get navExit => 'Yes, exit';

  @override
  String get loadingInfo => 'Loading information…';

  @override
  String get conditionsOnRoute => 'Conditions on route';

  @override
  String get calculateRoute => 'Calculate route';

  @override
  String get sectionNavigationVoice => 'Navigation voice';

  @override
  String get voiceGuidance => 'Voice guidance';

  @override
  String get voiceGuidanceDesc =>
      'Read turn-by-turn instructions aloud during navigation';

  @override
  String get testVoiceEngine => 'Test voice engine';

  @override
  String get testVoiceEngineDesc =>
      'Tap to check the TTS engine and get setup instructions';

  @override
  String get ttsDialogTitle => 'Missing voice engine';

  @override
  String get ttsDialogBody =>
      'No working Text-to-Speech engine was found.\n\nRoadstr only relies on open source software — install one of these free engines from F-Droid:';

  @override
  String get ttsRhvoiceDesc => 'Natural-sounding voice, limited language list';

  @override
  String get ttsEspeakDesc => 'Covers 100+ languages, robotic-sounding voice';

  @override
  String get ttsInstallNote =>
      '⚠️ After installing:\n1. Android Settings → Accessibility → Text-to-speech output\n2. Select the engine you just installed\n3. Download your language\'s voice data\n4. Restart Roadstr completely';

  @override
  String get ttsTestNow => 'Test now';

  @override
  String get voiceUnsupportedTitle => 'Voice guidance unavailable';

  @override
  String get voiceUnsupportedBody =>
      'Your language isn\'t yet supported for spoken turn-by-turn directions. Navigation instructions will still appear as text on screen.';

  @override
  String get kokoroModelTitle => 'Voice model (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Not downloaded · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Downloading...';

  @override
  String get kokoroModelStatusReady => 'Voice model ready';

  @override
  String get kokoroModelDownloadBtn => 'Download';

  @override
  String get kokoroModelSupportedLangs =>
      'Supports: Italian, English, Spanish, French, Japanese, Chinese, Portuguese';

  @override
  String get autoDarkMode => 'Auto dark theme';

  @override
  String get autoDarkModeDesc =>
      'Activates the dark theme at sunset and sunrise';

  @override
  String get settingsImperialUnits => 'Imperial units';

  @override
  String get settingsImperialUnitsDesc =>
      'Miles and feet instead of kilometres and metres';

  @override
  String get arrivedTitle => '🎉 You arrived!';

  @override
  String get arrivedBody => 'You have reached your destination.';

  @override
  String get arrivedFeedbackPrompt => 'How did it go?';

  @override
  String get feedbackBad => 'Bad';

  @override
  String get feedbackGood => 'Good!';

  @override
  String get feedbackDialogTitle => 'Tell us what went wrong';

  @override
  String get feedbackHint => 'Describe the problem…';

  @override
  String get feedbackSent => 'Feedback sent — thank you! 🙏';

  @override
  String get feedbackSubmit => 'Send';

  @override
  String get transportModeCar => 'Car';

  @override
  String get transportModeWalk => 'On foot';

  @override
  String etaArrivalLabel(String time) {
    return 'Arr. $time';
  }

  @override
  String get supportRoadstr => 'Support Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address copied to clipboard';
  }

  @override
  String get disclaimerTitle => 'Important notice';

  @override
  String get disclaimerAccept => 'I have read and accept';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental, open-source, community-maintained navigation app based on OpenStreetMap data and the Nostr protocol, made available for use in any country. By downloading, installing or using this app, the user unconditionally accepts all of the following terms, without limitation of territory:\n\n🚗  ROAD SAFETY FIRST\nThe driver must always keep their eyes on the road and comply with all applicable traffic laws and posted signage, which always take precedence over any instruction from the app. Never operate the device while driving; secure it in an approved, visible mount before setting off, and never divert attention from the road to interact with it while the vehicle is moving.\n\n⚠️  ASSUMPTION OF RISK — WORLDWIDE\nBy using Roadstr, in any country and under any legal system, the user knowingly and voluntarily assumes ALL risks connected with its use, including but not limited to: traffic accidents, personal injury, death, property damage, vehicle damage, fines, administrative penalties, towing, impoundment, criminal liability, or any other consequence arising directly or indirectly from reliance on the app. The user alone bears full responsibility for every driving and navigation decision.\n\n🚫  NO WARRANTY\nRoadstr is provided strictly \"AS IS\" and \"AS AVAILABLE\", without any warranty of any kind, whether express, implied, or statutory — including, without limitation, warranties of accuracy, completeness, reliability, availability, merchantability, fitness for a particular purpose, and non-infringement. Map data, routing, speed limits, speed cameras, and restricted-traffic-zone (ZTL/ZAC/LTZ) information come from open, community-maintained sources (OpenStreetMap, Overpass API) that may be incomplete, outdated, or inaccurate for any country, region, or municipality, at any time and without notice. The user is solely responsible for independently verifying, before and during travel, the legality and accessibility of any suggested route against official local signage and regulations.\n\n📍  ACCURACY & GPS\nGPS positioning may be inaccurate or unavailable. All directions, distances, and alerts are provided for guidance only and must never be relied upon as the sole basis for a driving decision.\n\n🛡️  LIMITATION OF LIABILITY\nTo the maximum extent permitted by applicable law in any jurisdiction, the developers, contributors, and any party involved in creating or distributing Roadstr shall not be liable for any direct, indirect, incidental, consequential, special, exemplary, or punitive damages of any kind — including personal injury, death, or financial loss — arising out of or related to the use or inability to use the app, even if advised of the possibility of such damages. Where a jurisdiction does not allow some or all of this limitation, liability is limited to the smallest extent permitted by law in that jurisdiction.\n\n🤝  INDEMNIFICATION\nThe user agrees to indemnify and hold harmless the developers and contributors from any claim, damage, loss, or expense (including legal fees) arising from the user\'s use of the app or violation of these terms.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️  SEVERABILITY\nIf any provision of these terms is found unenforceable in a given jurisdiction, that provision shall be limited or severed to the minimum extent necessary, and all remaining provisions shall remain in full force and effect.\n\nBy using Roadstr, anywhere in the world, the user confirms they have read, understood, and unconditionally accepted every term above, and assumes full and complete responsibility — and all risk — for the use of the application and any consequence arising from it.';

  @override
  String get readOnWikipedia => 'Read on Wikipedia';

  @override
  String get openInBrowser => 'Open in browser';

  @override
  String get wikipediaLoadFailed => 'Wikipedia could not be loaded securely.';

  @override
  String get retry => 'Retry';

  @override
  String get poiDetailsFromOsm => 'Information from OpenStreetMap';

  @override
  String get poiCategory => 'Category';

  @override
  String get poiOperator => 'Operator';

  @override
  String get poiCuisine => 'Cuisine';

  @override
  String get poiAccessibility => 'Accessibility';

  @override
  String get poiWheelchairYes => 'Wheelchair accessible';

  @override
  String get poiWheelchairLimited => 'Limited wheelchair access';

  @override
  String get poiWheelchairNo => 'Not wheelchair accessible';

  @override
  String get poiContact => 'Contact';

  @override
  String get poiAddress => 'Address';

  @override
  String get poiWebsite => 'Website';

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
  String get gpsSignalLost => 'GPS signal lost';

  @override
  String get poiOpenNow => 'Open now';

  @override
  String get poiClosedNow => 'Closed';

  @override
  String poiOpensAt(String when) {
    return 'Opens: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Closes: $when';
  }

  @override
  String searchOnEngine(String engine) {
    return 'Search on $engine';
  }

  @override
  String get plannerFromHint => 'From…';

  @override
  String get plannerToHint => 'Destination…';

  @override
  String departEta(String dep, String arr) {
    return 'Depart $dep  →  ETA $arr';
  }

  @override
  String get modeCar => 'Car';

  @override
  String get modeBike => 'Bike';

  @override
  String get modeWalk => 'On foot';

  @override
  String windSpeed(String speed) {
    return 'wind $speed km/h';
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
  String get weatherClear => 'Clear';

  @override
  String get weatherPartlyCloudy => 'Partly cloudy';

  @override
  String get weatherCloudy => 'Cloudy';

  @override
  String get weatherFog => 'Fog';

  @override
  String get weatherLightRain => 'Light rain';

  @override
  String get weatherRain => 'Rain';

  @override
  String get weatherSnow => 'Snow';

  @override
  String get weatherShowers => 'Showers';

  @override
  String get weatherThunderstorm => 'Thunderstorm';

  @override
  String get ztlAheadWarning =>
      'Restricted traffic zone ahead — the route enters it';

  @override
  String get ztlInsideWarning => 'Restricted traffic zone';

  @override
  String get onboardingAppSubtitle => 'Open-source Nostr navigation';

  @override
  String get onboardingWelcomeTitle => 'Welcome';

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
  String get onboardingGetStarted => 'Get started';

  @override
  String get onboardingNostrTitle => 'Nostr Identity';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Connected';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (recommended)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec key';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Invalid nsec key — please check and try again.';

  @override
  String get onboardingSkip => 'Skip for now';

  @override
  String get onboardingContinue => 'Continue';

  @override
  String get onboardingEnterNsec => 'Enter nsec key';

  @override
  String get onboardingSetupTitle => 'Set up Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Location';

  @override
  String get onboardingLocationGranted => 'Location access granted';

  @override
  String get onboardingLocationRequired => 'Required for GPS navigation';

  @override
  String get onboardingGrantButton => 'Grant';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS users';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

  @override
  String get onboardingVoiceTitle => 'AI voice guidance (optional)';

  @override
  String get onboardingVoiceReady => 'Kokoro voice model ready';

  @override
  String get onboardingVoiceDownloading => 'Downloading voice model…';

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
  String get onboardingReadyTitle => 'You\'re ready to navigate!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Let\'s go!';

  @override
  String get onboardingProfileLoading => 'Loading profile…';

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
  String get syncNowButton => 'Push to Nostr';

  @override
  String get syncPullButton => 'Pull from Nostr';

  @override
  String get syncPassphraseTitle => 'Sync passphrase (optional)';

  @override
  String get syncPassphraseDesc =>
      'Second encryption layer for synced favorites: even if your Nostr key were ever compromised, the snapshot on the relays stays unreadable without this passphrase. You\'ll enter it once on each new device. Leave empty to disable.';

  @override
  String get syncSuccessSnack => 'Favorites synced';

  @override
  String get syncFailedSnack => 'Sync failed';

  @override
  String syncLastSyncLabel(String when) {
    return 'Last synced: $when';
  }

  @override
  String get syncNoIdentity =>
      'Sign in with Nostr (Settings → Profile) to enable sync';

  @override
  String get onboardingVpnNotice =>
      'For maximum privacy — road reports are propagated through the Nostr network — using a VPN is recommended. Mullvad, payable in Bitcoin, is the recommended choice.';

  @override
  String get trafficNormal => 'Normal traffic';

  @override
  String get trafficModerate => 'Moderate traffic';

  @override
  String get trafficHeavy => 'Heavy traffic';

  @override
  String get avoidHighwaysChip => 'Avoid highways';

  @override
  String get avoidTollsChip => 'Avoid tolls';

  @override
  String get preferShorterChip => 'Shortest route';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Show route preview';

  @override
  String get avoidHighwaysAndTolls => 'Avoid highways and toll roads';

  @override
  String get avoidRouteUnavailable =>
      'No route avoiding both highways and toll roads was found.';

  @override
  String get avoidanceUnavoidableSection =>
      'Minimizes highways/tolls · unavoidable section';
}
