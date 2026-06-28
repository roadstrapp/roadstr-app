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
  String get sectionPrivacy => 'Privacy';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Language';

  @override
  String get themeLightNostr => 'Light · Nostr Violet';

  @override
  String get themeLightBitcoin => 'Light · Bitcoin Orange';

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
  String get privacyMode => 'Privacy mode';

  @override
  String get privacyModeDescription => 'Do not send anonymous telemetry data';

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
}
