import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_bg.dart';
import 'app_localizations_cs.dart';
import 'app_localizations_da.dart';
import 'app_localizations_de.dart';
import 'app_localizations_el.dart';
import 'app_localizations_en.dart';
import 'app_localizations_es.dart';
import 'app_localizations_et.dart';
import 'app_localizations_fi.dart';
import 'app_localizations_fr.dart';
import 'app_localizations_ga.dart';
import 'app_localizations_hr.dart';
import 'app_localizations_hu.dart';
import 'app_localizations_it.dart';
import 'app_localizations_ja.dart';
import 'app_localizations_lt.dart';
import 'app_localizations_lv.dart';
import 'app_localizations_mt.dart';
import 'app_localizations_nl.dart';
import 'app_localizations_pl.dart';
import 'app_localizations_pt.dart';
import 'app_localizations_ro.dart';
import 'app_localizations_ru.dart';
import 'app_localizations_sk.dart';
import 'app_localizations_sl.dart';
import 'app_localizations_sv.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
      : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('bg'),
    Locale('cs'),
    Locale('da'),
    Locale('de'),
    Locale('el'),
    Locale('en'),
    Locale('es'),
    Locale('et'),
    Locale('fi'),
    Locale('fr'),
    Locale('ga'),
    Locale('hr'),
    Locale('hu'),
    Locale('it'),
    Locale('ja'),
    Locale('lt'),
    Locale('lv'),
    Locale('mt'),
    Locale('nl'),
    Locale('pl'),
    Locale('pt'),
    Locale('ro'),
    Locale('ru'),
    Locale('sk'),
    Locale('sl'),
    Locale('sv'),
    Locale('zh')
  ];

  /// No description provided for @searchHint.
  ///
  /// In en, this message translates to:
  /// **'Where do you want to go?'**
  String get searchHint;

  /// No description provided for @calculatingRoute.
  ///
  /// In en, this message translates to:
  /// **'Calculating route…'**
  String get calculatingRoute;

  /// No description provided for @routeNotFoundTitle.
  ///
  /// In en, this message translates to:
  /// **'Route not found'**
  String get routeNotFoundTitle;

  /// No description provided for @noRouteFound.
  ///
  /// In en, this message translates to:
  /// **'No route found. Check your connection.'**
  String get noRouteFound;

  /// No description provided for @emptyServerResponse.
  ///
  /// In en, this message translates to:
  /// **'Empty server response. Check your connection.'**
  String get emptyServerResponse;

  /// No description provided for @routeError.
  ///
  /// In en, this message translates to:
  /// **'Route calculation error: {error}'**
  String routeError(String error);

  /// No description provided for @gpsNotAvailable.
  ///
  /// In en, this message translates to:
  /// **'GPS unavailable — Settings → App → Roadstr → Permissions → Location'**
  String get gpsNotAvailable;

  /// No description provided for @acquiringGps.
  ///
  /// In en, this message translates to:
  /// **'Acquiring GPS…'**
  String get acquiringGps;

  /// No description provided for @graphhopperServerNotConfigured.
  ///
  /// In en, this message translates to:
  /// **'GraphHopper server not configured — using OSRM'**
  String get graphhopperServerNotConfigured;

  /// No description provided for @graphhopperApiKeyNotConfigured.
  ///
  /// In en, this message translates to:
  /// **'GraphHopper API key not configured — using OSRM'**
  String get graphhopperApiKeyNotConfigured;

  /// No description provided for @openrouteApiKeyNotConfigured.
  ///
  /// In en, this message translates to:
  /// **'OpenRouteService API key not configured — using OSRM'**
  String get openrouteApiKeyNotConfigured;

  /// No description provided for @chooseRoute.
  ///
  /// In en, this message translates to:
  /// **'Choose route'**
  String get chooseRoute;

  /// No description provided for @routeOptionsCount.
  ///
  /// In en, this message translates to:
  /// **'{count} options'**
  String routeOptionsCount(int count);

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @startNavigation.
  ///
  /// In en, this message translates to:
  /// **'Start navigation'**
  String get startNavigation;

  /// No description provided for @fastestRoute.
  ///
  /// In en, this message translates to:
  /// **'Fastest'**
  String get fastestRoute;

  /// No description provided for @now.
  ///
  /// In en, this message translates to:
  /// **'Now'**
  String get now;

  /// No description provided for @history.
  ///
  /// In en, this message translates to:
  /// **'History'**
  String get history;

  /// No description provided for @clearHistory.
  ///
  /// In en, this message translates to:
  /// **'Clear'**
  String get clearHistory;

  /// No description provided for @selectedPosition.
  ///
  /// In en, this message translates to:
  /// **'Selected position'**
  String get selectedPosition;

  /// No description provided for @bottomBarProfile.
  ///
  /// In en, this message translates to:
  /// **'Profile'**
  String get bottomBarProfile;

  /// No description provided for @bottomBarMenu.
  ///
  /// In en, this message translates to:
  /// **'Menu'**
  String get bottomBarMenu;

  /// No description provided for @settingsTitle.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settingsTitle;

  /// No description provided for @sectionTheme.
  ///
  /// In en, this message translates to:
  /// **'Theme'**
  String get sectionTheme;

  /// No description provided for @sectionMap.
  ///
  /// In en, this message translates to:
  /// **'Map'**
  String get sectionMap;

  /// No description provided for @sectionInfo.
  ///
  /// In en, this message translates to:
  /// **'Info'**
  String get sectionInfo;

  /// No description provided for @sectionLanguage.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get sectionLanguage;

  /// No description provided for @themeLightNostr.
  ///
  /// In en, this message translates to:
  /// **'Light · Nostr Violet'**
  String get themeLightNostr;

  /// No description provided for @themeLightBitcoin.
  ///
  /// In en, this message translates to:
  /// **'Light · Bitcoin Orange'**
  String get themeLightBitcoin;

  /// No description provided for @themeDarkNostr.
  ///
  /// In en, this message translates to:
  /// **'Dark · Nostr Violet'**
  String get themeDarkNostr;

  /// No description provided for @themeDarkBitcoin.
  ///
  /// In en, this message translates to:
  /// **'Dark · Bitcoin Orange'**
  String get themeDarkBitcoin;

  /// No description provided for @langSystem.
  ///
  /// In en, this message translates to:
  /// **'System default'**
  String get langSystem;

  /// No description provided for @langItalian.
  ///
  /// In en, this message translates to:
  /// **'Italiano'**
  String get langItalian;

  /// No description provided for @langEnglish.
  ///
  /// In en, this message translates to:
  /// **'English'**
  String get langEnglish;

  /// No description provided for @keepScreenOn.
  ///
  /// In en, this message translates to:
  /// **'Keep screen on'**
  String get keepScreenOn;

  /// No description provided for @keepScreenOnDescription.
  ///
  /// In en, this message translates to:
  /// **'Prevents sleep during navigation'**
  String get keepScreenOnDescription;

  /// No description provided for @rotateMap.
  ///
  /// In en, this message translates to:
  /// **'Map follows direction'**
  String get rotateMap;

  /// No description provided for @rotateMapDescription.
  ///
  /// In en, this message translates to:
  /// **'Map rotates based on driving direction'**
  String get rotateMapDescription;

  /// No description provided for @mapTileUrlLabel.
  ///
  /// In en, this message translates to:
  /// **'Map tile URL'**
  String get mapTileUrlLabel;

  /// No description provided for @routingProviderLabel.
  ///
  /// In en, this message translates to:
  /// **'Routing provider'**
  String get routingProviderLabel;

  /// No description provided for @osrmProvider.
  ///
  /// In en, this message translates to:
  /// **'OSRM (public, no key required)'**
  String get osrmProvider;

  /// No description provided for @graphhopperLocalProvider.
  ///
  /// In en, this message translates to:
  /// **'GraphHopper (local/private)'**
  String get graphhopperLocalProvider;

  /// No description provided for @graphhopperCloudProvider.
  ///
  /// In en, this message translates to:
  /// **'GraphHopper Cloud (API key)'**
  String get graphhopperCloudProvider;

  /// No description provided for @openrouteProvider.
  ///
  /// In en, this message translates to:
  /// **'OpenRouteService (API key)'**
  String get openrouteProvider;

  /// No description provided for @graphhopperServerHint.
  ///
  /// In en, this message translates to:
  /// **'http://localhost:8989/route'**
  String get graphhopperServerHint;

  /// No description provided for @graphhopperApiKeyHint.
  ///
  /// In en, this message translates to:
  /// **'GraphHopper API key (optional)'**
  String get graphhopperApiKeyHint;

  /// No description provided for @verify.
  ///
  /// In en, this message translates to:
  /// **'Verify'**
  String get verify;

  /// No description provided for @graphhopperServerUrlRequired.
  ///
  /// In en, this message translates to:
  /// **'Enter the server URL before verifying.'**
  String get graphhopperServerUrlRequired;

  /// No description provided for @successTitle.
  ///
  /// In en, this message translates to:
  /// **'Success'**
  String get successTitle;

  /// No description provided for @graphhopperServerReachable.
  ///
  /// In en, this message translates to:
  /// **'GraphHopper server reachable'**
  String get graphhopperServerReachable;

  /// No description provided for @errorTitle.
  ///
  /// In en, this message translates to:
  /// **'Error'**
  String get errorTitle;

  /// No description provided for @close.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get close;

  /// No description provided for @infoVersion.
  ///
  /// In en, this message translates to:
  /// **'Version'**
  String get infoVersion;

  /// No description provided for @infoProtocol.
  ///
  /// In en, this message translates to:
  /// **'Protocol'**
  String get infoProtocol;

  /// No description provided for @infoMaps.
  ///
  /// In en, this message translates to:
  /// **'Maps'**
  String get infoMaps;

  /// No description provided for @infoRouting.
  ///
  /// In en, this message translates to:
  /// **'Routing'**
  String get infoRouting;

  /// No description provided for @infoSource.
  ///
  /// In en, this message translates to:
  /// **'Source'**
  String get infoSource;

  /// No description provided for @providerOsrm.
  ///
  /// In en, this message translates to:
  /// **'OSRM'**
  String get providerOsrm;

  /// No description provided for @providerGraphhopperSelfHosted.
  ///
  /// In en, this message translates to:
  /// **'GraphHopper (self-hosted)'**
  String get providerGraphhopperSelfHosted;

  /// No description provided for @providerGraphhopperCloud.
  ///
  /// In en, this message translates to:
  /// **'GraphHopper (cloud)'**
  String get providerGraphhopperCloud;

  /// No description provided for @providerOpenroute.
  ///
  /// In en, this message translates to:
  /// **'OpenRouteService'**
  String get providerOpenroute;

  /// No description provided for @profileTitle.
  ///
  /// In en, this message translates to:
  /// **'Profile'**
  String get profileTitle;

  /// No description provided for @notConnected.
  ///
  /// In en, this message translates to:
  /// **'Not connected'**
  String get notConnected;

  /// No description provided for @loginWithNostrTitle.
  ///
  /// In en, this message translates to:
  /// **'LOGIN WITH NOSTR'**
  String get loginWithNostrTitle;

  /// No description provided for @amberNip55Title.
  ///
  /// In en, this message translates to:
  /// **'Amber / NIP-55'**
  String get amberNip55Title;

  /// No description provided for @amberLoginDescription.
  ///
  /// In en, this message translates to:
  /// **'Private key never leaves your device · Recommended'**
  String get amberLoginDescription;

  /// No description provided for @nsecLoginOption.
  ///
  /// In en, this message translates to:
  /// **'Insert nsec'**
  String get nsecLoginOption;

  /// No description provided for @nsecLoginDescription.
  ///
  /// In en, this message translates to:
  /// **'Private key stored locally · Less secure'**
  String get nsecLoginDescription;

  /// No description provided for @connectedViaAmber.
  ///
  /// In en, this message translates to:
  /// **'Connected via Amber (NIP-55)'**
  String get connectedViaAmber;

  /// No description provided for @connectedViaNsec.
  ///
  /// In en, this message translates to:
  /// **'Connected via nsec'**
  String get connectedViaNsec;

  /// No description provided for @publicKeyLabel.
  ///
  /// In en, this message translates to:
  /// **'PUBLIC KEY'**
  String get publicKeyLabel;

  /// No description provided for @npubCopiedToClipboard.
  ///
  /// In en, this message translates to:
  /// **'npub copied to clipboard'**
  String get npubCopiedToClipboard;

  /// No description provided for @logoutTitle.
  ///
  /// In en, this message translates to:
  /// **'Disconnect'**
  String get logoutTitle;

  /// No description provided for @logoutConfirmation.
  ///
  /// In en, this message translates to:
  /// **'Remove Nostr credentials from this device?'**
  String get logoutConfirmation;

  /// No description provided for @logoutButton.
  ///
  /// In en, this message translates to:
  /// **'Disconnect'**
  String get logoutButton;

  /// No description provided for @nostrIdentityInfo.
  ///
  /// In en, this message translates to:
  /// **'With a Nostr identity you can publish traffic alerts, reports and points of interest in a decentralized way on the Nostr network, without central servers.'**
  String get nostrIdentityInfo;

  /// No description provided for @warningTitle.
  ///
  /// In en, this message translates to:
  /// **'Warning'**
  String get warningTitle;

  /// No description provided for @nsecWarning.
  ///
  /// In en, this message translates to:
  /// **'You are about to rawdog your Nostr private key directly into an app. Anyone with physical or remote access to your device could read it and permanently take control of your Nostr identity.'**
  String get nsecWarning;

  /// No description provided for @amberSecureMethodHint.
  ///
  /// In en, this message translates to:
  /// **'✦  The safe method is Amber (NIP-55): the nsec never leaves the app signer vault.'**
  String get amberSecureMethodHint;

  /// No description provided for @nsecRiskAcknowledgment.
  ///
  /// In en, this message translates to:
  /// **'I understand the risk and want to continue anyway'**
  String get nsecRiskAcknowledgment;

  /// No description provided for @continueButton.
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get continueButton;

  /// No description provided for @amberDialogTitle.
  ///
  /// In en, this message translates to:
  /// **'Amber / NIP-55'**
  String get amberDialogTitle;

  /// No description provided for @amberDescription.
  ///
  /// In en, this message translates to:
  /// **'Amber is an Android NIP-55 compliant app signer. Your private key stays isolated inside Amber and is never shared.'**
  String get amberDescription;

  /// No description provided for @requestKeyFromAmber.
  ///
  /// In en, this message translates to:
  /// **'Request public key from Amber'**
  String get requestKeyFromAmber;

  /// No description provided for @amberNotFound.
  ///
  /// In en, this message translates to:
  /// **'Amber not found. Install it from the Play Store or enter your npub manually.'**
  String get amberNotFound;

  /// No description provided for @waitingForAmberResponse.
  ///
  /// In en, this message translates to:
  /// **'Waiting for Amber response…'**
  String get waitingForAmberResponse;

  /// No description provided for @pasteNpubManually.
  ///
  /// In en, this message translates to:
  /// **'Paste your npub manually:'**
  String get pasteNpubManually;

  /// No description provided for @confirmNpub.
  ///
  /// In en, this message translates to:
  /// **'Confirm npub'**
  String get confirmNpub;

  /// No description provided for @enterNsecTitle.
  ///
  /// In en, this message translates to:
  /// **'Insert nsec'**
  String get enterNsecTitle;

  /// No description provided for @loginButton.
  ///
  /// In en, this message translates to:
  /// **'Login'**
  String get loginButton;

  /// No description provided for @invalidNpubTitle.
  ///
  /// In en, this message translates to:
  /// **'Invalid npub'**
  String get invalidNpubTitle;

  /// No description provided for @invalidNsecTitle.
  ///
  /// In en, this message translates to:
  /// **'Invalid nsec'**
  String get invalidNsecTitle;

  /// No description provided for @invalidNpubMessage.
  ///
  /// In en, this message translates to:
  /// **'Make sure you pasted the correct npub.'**
  String get invalidNpubMessage;

  /// No description provided for @invalidNsecMessage.
  ///
  /// In en, this message translates to:
  /// **'Make sure you pasted the correct nsec.'**
  String get invalidNsecMessage;

  /// No description provided for @amberResponseError.
  ///
  /// In en, this message translates to:
  /// **'Amber response error'**
  String get amberResponseError;

  /// No description provided for @ok.
  ///
  /// In en, this message translates to:
  /// **'OK'**
  String get ok;

  /// No description provided for @or.
  ///
  /// In en, this message translates to:
  /// **'or'**
  String get or;

  /// No description provided for @gpsNotActiveTitle.
  ///
  /// In en, this message translates to:
  /// **'GPS not active'**
  String get gpsNotActiveTitle;

  /// No description provided for @gpsDisabledMessage.
  ///
  /// In en, this message translates to:
  /// **'Activate GPS in your device settings to get your location and use navigation.'**
  String get gpsDisabledMessage;

  /// No description provided for @openSettings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get openSettings;

  /// No description provided for @myLocation.
  ///
  /// In en, this message translates to:
  /// **'My location'**
  String get myLocation;

  /// No description provided for @loginToReport.
  ///
  /// In en, this message translates to:
  /// **'Login with Nostr (Profile section) to report events'**
  String get loginToReport;

  /// No description provided for @navigateHere.
  ///
  /// In en, this message translates to:
  /// **'Navigate here'**
  String get navigateHere;

  /// No description provided for @reportEventHere.
  ///
  /// In en, this message translates to:
  /// **'Report event here'**
  String get reportEventHere;

  /// No description provided for @zapSendSats.
  ///
  /// In en, this message translates to:
  /// **'Zap ⚡ (send sats)'**
  String get zapSendSats;

  /// No description provided for @sendZap.
  ///
  /// In en, this message translates to:
  /// **'Send a Zap'**
  String get sendZap;

  /// No description provided for @chooseAmountSats.
  ///
  /// In en, this message translates to:
  /// **'Choose amount in satoshi (sats):'**
  String get chooseAmountSats;

  /// No description provided for @customAmount.
  ///
  /// In en, this message translates to:
  /// **'Custom amount…'**
  String get customAmount;

  /// No description provided for @zapSending.
  ///
  /// In en, this message translates to:
  /// **'Sending…'**
  String get zapSending;

  /// No description provided for @zapSat.
  ///
  /// In en, this message translates to:
  /// **'⚡{sats} sat'**
  String zapSat(int sats);

  /// No description provided for @fetchingLightningAddress.
  ///
  /// In en, this message translates to:
  /// **'Fetching Lightning address…'**
  String get fetchingLightningAddress;

  /// No description provided for @noLightningAddress.
  ///
  /// In en, this message translates to:
  /// **'This reporter has no Lightning address'**
  String get noLightningAddress;

  /// No description provided for @requestingInvoice.
  ///
  /// In en, this message translates to:
  /// **'Requesting invoice…'**
  String get requestingInvoice;

  /// No description provided for @lnurlUnavailable.
  ///
  /// In en, this message translates to:
  /// **'LNURL not available'**
  String get lnurlUnavailable;

  /// No description provided for @invoiceFailed.
  ///
  /// In en, this message translates to:
  /// **'Unable to generate invoice'**
  String get invoiceFailed;

  /// No description provided for @openingWallet.
  ///
  /// In en, this message translates to:
  /// **'Opening wallet…'**
  String get openingWallet;

  /// No description provided for @payingViaNwc.
  ///
  /// In en, this message translates to:
  /// **'Paying via NWC…'**
  String get payingViaNwc;

  /// No description provided for @noLightningWallet.
  ///
  /// In en, this message translates to:
  /// **'No Lightning wallet found'**
  String get noLightningWallet;

  /// No description provided for @zapSent.
  ///
  /// In en, this message translates to:
  /// **'⚡ {sats} sats sent!'**
  String zapSent(int sats);

  /// No description provided for @stillThere.
  ///
  /// In en, this message translates to:
  /// **'Still there'**
  String get stillThere;

  /// No description provided for @notThereAnymore.
  ///
  /// In en, this message translates to:
  /// **'Not there anymore'**
  String get notThereAnymore;

  /// No description provided for @loginToConfirm.
  ///
  /// In en, this message translates to:
  /// **'Login with Nostr to confirm or dispute'**
  String get loginToConfirm;

  /// No description provided for @reportAnEvent.
  ///
  /// In en, this message translates to:
  /// **'Report an event'**
  String get reportAnEvent;

  /// No description provided for @optionalComment.
  ///
  /// In en, this message translates to:
  /// **'Optional comment…'**
  String get optionalComment;

  /// No description provided for @publish.
  ///
  /// In en, this message translates to:
  /// **'Publish'**
  String get publish;

  /// No description provided for @publishing.
  ///
  /// In en, this message translates to:
  /// **'Publishing…'**
  String get publishing;

  /// No description provided for @reportPublished.
  ///
  /// In en, this message translates to:
  /// **'Report published ✓'**
  String get reportPublished;

  /// No description provided for @myReports.
  ///
  /// In en, this message translates to:
  /// **'MY REPORTS'**
  String get myReports;

  /// No description provided for @noReportsYet.
  ///
  /// In en, this message translates to:
  /// **'No reports published'**
  String get noReportsYet;

  /// No description provided for @zapBalance.
  ///
  /// In en, this message translates to:
  /// **'Zap Balance'**
  String get zapBalance;

  /// No description provided for @satoshiFromReports.
  ///
  /// In en, this message translates to:
  /// **'Satoshi received from your reports'**
  String get satoshiFromReports;

  /// No description provided for @reputationHigh.
  ///
  /// In en, this message translates to:
  /// **'High'**
  String get reputationHigh;

  /// No description provided for @reputationMedium.
  ///
  /// In en, this message translates to:
  /// **'Medium'**
  String get reputationMedium;

  /// No description provided for @reputationLow.
  ///
  /// In en, this message translates to:
  /// **'Low'**
  String get reputationLow;

  /// No description provided for @reputationLabel.
  ///
  /// In en, this message translates to:
  /// **'Reputation {level}'**
  String reputationLabel(String level);

  /// No description provided for @reliability.
  ///
  /// In en, this message translates to:
  /// **'Reliability: {pct}%'**
  String reliability(int pct);

  /// No description provided for @confirmedLabel.
  ///
  /// In en, this message translates to:
  /// **'Confirmed'**
  String get confirmedLabel;

  /// No description provided for @removedLabel.
  ///
  /// In en, this message translates to:
  /// **'Removed'**
  String get removedLabel;

  /// No description provided for @positionLabel.
  ///
  /// In en, this message translates to:
  /// **'Position'**
  String get positionLabel;

  /// No description provided for @loadingLabel.
  ///
  /// In en, this message translates to:
  /// **'Loading…'**
  String get loadingLabel;

  /// No description provided for @sectionWebSearch.
  ///
  /// In en, this message translates to:
  /// **'Web Search'**
  String get sectionWebSearch;

  /// No description provided for @sectionLightning.
  ///
  /// In en, this message translates to:
  /// **'Lightning'**
  String get sectionLightning;

  /// No description provided for @nwcLabel.
  ///
  /// In en, this message translates to:
  /// **'Nostr Wallet Connect (NWC)'**
  String get nwcLabel;

  /// No description provided for @nwcDesc.
  ///
  /// In en, this message translates to:
  /// **'Paste your NWC URI (Alby Hub, Mutiny, Cashu…) to pay Zaps directly from the app.'**
  String get nwcDesc;

  /// No description provided for @searchEngineQwantDesc.
  ///
  /// In en, this message translates to:
  /// **'European, privacy-first. No tracking, no ad profiles. Recommended.'**
  String get searchEngineQwantDesc;

  /// No description provided for @searchEngineBraveDesc.
  ///
  /// In en, this message translates to:
  /// **'Independent index, open-source. No Google or Bing dependency. Zero profiling.'**
  String get searchEngineBraveDesc;

  /// No description provided for @searchEngineDdgDesc.
  ///
  /// In en, this message translates to:
  /// **'Privacy-focused and popular. Results partially from Bing — keep that in mind.'**
  String get searchEngineDdgDesc;

  /// No description provided for @searchEngineStartpageDesc.
  ///
  /// In en, this message translates to:
  /// **'Google results without handing your data to Google. A reasonable compromise.'**
  String get searchEngineStartpageDesc;

  /// No description provided for @searchEngineGoogleDesc.
  ///
  /// In en, this message translates to:
  /// **'Very effective. But remember: Google knows you better than your mother and sells everything to advertisers. Your call though. 🍪'**
  String get searchEngineGoogleDesc;

  /// No description provided for @categoryPolice.
  ///
  /// In en, this message translates to:
  /// **'Police'**
  String get categoryPolice;

  /// No description provided for @categorySpeedCamera.
  ///
  /// In en, this message translates to:
  /// **'Speed Camera'**
  String get categorySpeedCamera;

  /// No description provided for @categoryTrafficJam.
  ///
  /// In en, this message translates to:
  /// **'Traffic Jam'**
  String get categoryTrafficJam;

  /// No description provided for @categoryAccident.
  ///
  /// In en, this message translates to:
  /// **'Accident'**
  String get categoryAccident;

  /// No description provided for @categoryRoadClosure.
  ///
  /// In en, this message translates to:
  /// **'Road Closure'**
  String get categoryRoadClosure;

  /// No description provided for @categoryConstruction.
  ///
  /// In en, this message translates to:
  /// **'Construction'**
  String get categoryConstruction;

  /// No description provided for @categoryHazard.
  ///
  /// In en, this message translates to:
  /// **'Hazard'**
  String get categoryHazard;

  /// No description provided for @categoryRoadCondition.
  ///
  /// In en, this message translates to:
  /// **'Road Condition'**
  String get categoryRoadCondition;

  /// No description provided for @categoryPothole.
  ///
  /// In en, this message translates to:
  /// **'Pothole'**
  String get categoryPothole;

  /// No description provided for @categoryFog.
  ///
  /// In en, this message translates to:
  /// **'Fog'**
  String get categoryFog;

  /// No description provided for @categoryIce.
  ///
  /// In en, this message translates to:
  /// **'Ice'**
  String get categoryIce;

  /// No description provided for @categoryAnimal.
  ///
  /// In en, this message translates to:
  /// **'Animal'**
  String get categoryAnimal;

  /// No description provided for @categoryOther.
  ///
  /// In en, this message translates to:
  /// **'Other'**
  String get categoryOther;

  /// No description provided for @dateTimeLabel.
  ///
  /// In en, this message translates to:
  /// **'Date / time'**
  String get dateTimeLabel;

  /// No description provided for @minutesAgo.
  ///
  /// In en, this message translates to:
  /// **'{count} min ago'**
  String minutesAgo(int count);

  /// No description provided for @hoursAgo.
  ///
  /// In en, this message translates to:
  /// **'{count}h ago'**
  String hoursAgo(int count);

  /// No description provided for @daysAgo.
  ///
  /// In en, this message translates to:
  /// **'{count}d ago'**
  String daysAgo(int count);

  /// No description provided for @sectionFavorites.
  ///
  /// In en, this message translates to:
  /// **'Saved Places'**
  String get sectionFavorites;

  /// No description provided for @addFavorite.
  ///
  /// In en, this message translates to:
  /// **'Add place'**
  String get addFavorite;

  /// No description provided for @favoriteLabelHint.
  ///
  /// In en, this message translates to:
  /// **'Name (e.g. Home, Office)'**
  String get favoriteLabelHint;

  /// No description provided for @favoriteAddressHint.
  ///
  /// In en, this message translates to:
  /// **'Address'**
  String get favoriteAddressHint;

  /// No description provided for @favoriteGeocodingError.
  ///
  /// In en, this message translates to:
  /// **'Address not found. Try a more specific address.'**
  String get favoriteGeocodingError;

  /// No description provided for @trafficAlertTitle.
  ///
  /// In en, this message translates to:
  /// **'New traffic on route'**
  String get trafficAlertTitle;

  /// No description provided for @trafficAlertBody.
  ///
  /// In en, this message translates to:
  /// **'{category} reported {age} on your route.\n\nDo you want to find an alternative route?'**
  String trafficAlertBody(Object age, Object category);

  /// No description provided for @trafficContinue.
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get trafficContinue;

  /// No description provided for @trafficRecalculate.
  ///
  /// In en, this message translates to:
  /// **'Recalculate route'**
  String get trafficRecalculate;

  /// No description provided for @navExitTitle.
  ///
  /// In en, this message translates to:
  /// **'Exit navigation?'**
  String get navExitTitle;

  /// No description provided for @navExitBody.
  ///
  /// In en, this message translates to:
  /// **'Do you want to stop navigation and return to the map?'**
  String get navExitBody;

  /// No description provided for @navContinue.
  ///
  /// In en, this message translates to:
  /// **'Continue navigation'**
  String get navContinue;

  /// No description provided for @navExit.
  ///
  /// In en, this message translates to:
  /// **'Yes, exit'**
  String get navExit;

  /// No description provided for @loadingInfo.
  ///
  /// In en, this message translates to:
  /// **'Loading information…'**
  String get loadingInfo;

  /// No description provided for @conditionsOnRoute.
  ///
  /// In en, this message translates to:
  /// **'Conditions on route'**
  String get conditionsOnRoute;

  /// No description provided for @calculateRoute.
  ///
  /// In en, this message translates to:
  /// **'Calculate route'**
  String get calculateRoute;

  /// No description provided for @sectionNavigationVoice.
  ///
  /// In en, this message translates to:
  /// **'Navigation voice'**
  String get sectionNavigationVoice;

  /// No description provided for @voiceGuidance.
  ///
  /// In en, this message translates to:
  /// **'Voice guidance'**
  String get voiceGuidance;

  /// No description provided for @voiceGuidanceDesc.
  ///
  /// In en, this message translates to:
  /// **'Read turn-by-turn instructions aloud during navigation'**
  String get voiceGuidanceDesc;

  /// No description provided for @testVoiceEngine.
  ///
  /// In en, this message translates to:
  /// **'Test voice engine'**
  String get testVoiceEngine;

  /// No description provided for @testVoiceEngineDesc.
  ///
  /// In en, this message translates to:
  /// **'Tap to check the TTS engine and get setup instructions'**
  String get testVoiceEngineDesc;

  /// No description provided for @ttsDialogTitle.
  ///
  /// In en, this message translates to:
  /// **'Missing voice engine'**
  String get ttsDialogTitle;

  /// No description provided for @ttsDialogBody.
  ///
  /// In en, this message translates to:
  /// **'No working Text-to-Speech engine was found.\n\nRoadstr only relies on open source software — install one of these free engines from F-Droid:'**
  String get ttsDialogBody;

  /// No description provided for @ttsRhvoiceDesc.
  ///
  /// In en, this message translates to:
  /// **'Natural-sounding voice, limited language list'**
  String get ttsRhvoiceDesc;

  /// No description provided for @ttsEspeakDesc.
  ///
  /// In en, this message translates to:
  /// **'Covers 100+ languages, robotic-sounding voice'**
  String get ttsEspeakDesc;

  /// No description provided for @ttsInstallNote.
  ///
  /// In en, this message translates to:
  /// **'⚠️ After installing:\n1. Android Settings → Accessibility → Text-to-speech output\n2. Select the engine you just installed\n3. Download your language\'s voice data\n4. Restart Roadstr completely'**
  String get ttsInstallNote;

  /// No description provided for @ttsTestNow.
  ///
  /// In en, this message translates to:
  /// **'Test now'**
  String get ttsTestNow;

  /// No description provided for @voiceUnsupportedTitle.
  ///
  /// In en, this message translates to:
  /// **'Voice guidance unavailable'**
  String get voiceUnsupportedTitle;

  /// No description provided for @voiceUnsupportedBody.
  ///
  /// In en, this message translates to:
  /// **'Your language isn\'t yet supported for spoken turn-by-turn directions. Navigation instructions will still appear as text on screen.'**
  String get voiceUnsupportedBody;

  /// No description provided for @kokoroModelTitle.
  ///
  /// In en, this message translates to:
  /// **'Voice model (Kokoro AI)'**
  String get kokoroModelTitle;

  /// No description provided for @kokoroModelStatusNotDownloaded.
  ///
  /// In en, this message translates to:
  /// **'Not downloaded · ~82 MB'**
  String get kokoroModelStatusNotDownloaded;

  /// No description provided for @kokoroModelStatusDownloading.
  ///
  /// In en, this message translates to:
  /// **'Downloading...'**
  String get kokoroModelStatusDownloading;

  /// No description provided for @kokoroModelStatusReady.
  ///
  /// In en, this message translates to:
  /// **'Voice model ready'**
  String get kokoroModelStatusReady;

  /// No description provided for @kokoroModelDownloadBtn.
  ///
  /// In en, this message translates to:
  /// **'Download'**
  String get kokoroModelDownloadBtn;

  /// No description provided for @kokoroModelSupportedLangs.
  ///
  /// In en, this message translates to:
  /// **'Supports: Italian, English, Spanish, French, Japanese, Chinese, Portuguese'**
  String get kokoroModelSupportedLangs;

  /// No description provided for @autoDarkMode.
  ///
  /// In en, this message translates to:
  /// **'Auto dark theme'**
  String get autoDarkMode;

  /// No description provided for @autoDarkModeDesc.
  ///
  /// In en, this message translates to:
  /// **'Activates the dark theme at sunset and sunrise'**
  String get autoDarkModeDesc;

  /// No description provided for @settingsImperialUnits.
  ///
  /// In en, this message translates to:
  /// **'Imperial units'**
  String get settingsImperialUnits;

  /// No description provided for @settingsImperialUnitsDesc.
  ///
  /// In en, this message translates to:
  /// **'Miles and feet instead of kilometres and metres'**
  String get settingsImperialUnitsDesc;

  /// No description provided for @arrivedTitle.
  ///
  /// In en, this message translates to:
  /// **'🎉 You arrived!'**
  String get arrivedTitle;

  /// No description provided for @arrivedBody.
  ///
  /// In en, this message translates to:
  /// **'You have reached your destination.'**
  String get arrivedBody;

  /// No description provided for @arrivedFeedbackPrompt.
  ///
  /// In en, this message translates to:
  /// **'How did it go?'**
  String get arrivedFeedbackPrompt;

  /// No description provided for @feedbackBad.
  ///
  /// In en, this message translates to:
  /// **'Bad'**
  String get feedbackBad;

  /// No description provided for @feedbackGood.
  ///
  /// In en, this message translates to:
  /// **'Good!'**
  String get feedbackGood;

  /// No description provided for @feedbackDialogTitle.
  ///
  /// In en, this message translates to:
  /// **'Tell us what went wrong'**
  String get feedbackDialogTitle;

  /// No description provided for @feedbackHint.
  ///
  /// In en, this message translates to:
  /// **'Describe the problem…'**
  String get feedbackHint;

  /// No description provided for @feedbackSent.
  ///
  /// In en, this message translates to:
  /// **'Feedback sent — thank you! 🙏'**
  String get feedbackSent;

  /// No description provided for @feedbackSubmit.
  ///
  /// In en, this message translates to:
  /// **'Send'**
  String get feedbackSubmit;

  /// No description provided for @transportModeCar.
  ///
  /// In en, this message translates to:
  /// **'Car'**
  String get transportModeCar;

  /// No description provided for @transportModeWalk.
  ///
  /// In en, this message translates to:
  /// **'On foot'**
  String get transportModeWalk;

  /// No description provided for @etaArrivalLabel.
  ///
  /// In en, this message translates to:
  /// **'Arr. {time}'**
  String etaArrivalLabel(String time);

  /// No description provided for @supportRoadstr.
  ///
  /// In en, this message translates to:
  /// **'Support Roadstr'**
  String get supportRoadstr;

  /// No description provided for @lightningAddressCopied.
  ///
  /// In en, this message translates to:
  /// **'⚡ {address} copied to clipboard'**
  String lightningAddressCopied(String address);

  /// No description provided for @disclaimerTitle.
  ///
  /// In en, this message translates to:
  /// **'Important notice'**
  String get disclaimerTitle;

  /// No description provided for @disclaimerAccept.
  ///
  /// In en, this message translates to:
  /// **'I have read and accept'**
  String get disclaimerAccept;

  /// No description provided for @disclaimerBody.
  ///
  /// In en, this message translates to:
  /// **'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.'**
  String get disclaimerBody;

  /// No description provided for @readOnWikipedia.
  ///
  /// In en, this message translates to:
  /// **'Read on Wikipedia'**
  String get readOnWikipedia;

  /// No description provided for @searchOnEngine.
  ///
  /// In en, this message translates to:
  /// **'Search on {engine}'**
  String searchOnEngine(String engine);

  /// No description provided for @plannerFromHint.
  ///
  /// In en, this message translates to:
  /// **'From…'**
  String get plannerFromHint;

  /// No description provided for @plannerToHint.
  ///
  /// In en, this message translates to:
  /// **'Destination…'**
  String get plannerToHint;

  /// No description provided for @departEta.
  ///
  /// In en, this message translates to:
  /// **'Depart {dep}  →  ETA {arr}'**
  String departEta(String dep, String arr);

  /// No description provided for @modeCar.
  ///
  /// In en, this message translates to:
  /// **'Car'**
  String get modeCar;

  /// No description provided for @modeBike.
  ///
  /// In en, this message translates to:
  /// **'Bike'**
  String get modeBike;

  /// No description provided for @modeWalk.
  ///
  /// In en, this message translates to:
  /// **'On foot'**
  String get modeWalk;

  /// No description provided for @windSpeed.
  ///
  /// In en, this message translates to:
  /// **'wind {speed} km/h'**
  String windSpeed(String speed);

  /// No description provided for @durationMin.
  ///
  /// In en, this message translates to:
  /// **'{m} min'**
  String durationMin(int m);

  /// No description provided for @durationHourMin.
  ///
  /// In en, this message translates to:
  /// **'{h}h {m}min'**
  String durationHourMin(int h, int m);

  /// No description provided for @weatherClear.
  ///
  /// In en, this message translates to:
  /// **'Clear'**
  String get weatherClear;

  /// No description provided for @weatherPartlyCloudy.
  ///
  /// In en, this message translates to:
  /// **'Partly cloudy'**
  String get weatherPartlyCloudy;

  /// No description provided for @weatherCloudy.
  ///
  /// In en, this message translates to:
  /// **'Cloudy'**
  String get weatherCloudy;

  /// No description provided for @weatherFog.
  ///
  /// In en, this message translates to:
  /// **'Fog'**
  String get weatherFog;

  /// No description provided for @weatherLightRain.
  ///
  /// In en, this message translates to:
  /// **'Light rain'**
  String get weatherLightRain;

  /// No description provided for @weatherRain.
  ///
  /// In en, this message translates to:
  /// **'Rain'**
  String get weatherRain;

  /// No description provided for @weatherSnow.
  ///
  /// In en, this message translates to:
  /// **'Snow'**
  String get weatherSnow;

  /// No description provided for @weatherShowers.
  ///
  /// In en, this message translates to:
  /// **'Showers'**
  String get weatherShowers;

  /// No description provided for @weatherThunderstorm.
  ///
  /// In en, this message translates to:
  /// **'Thunderstorm'**
  String get weatherThunderstorm;

  /// No description provided for @ztlAheadWarning.
  ///
  /// In en, this message translates to:
  /// **'ZTL zone ahead — route leads into restricted area'**
  String get ztlAheadWarning;

  /// No description provided for @ztlInsideWarning.
  ///
  /// In en, this message translates to:
  /// **'Restricted traffic zone'**
  String get ztlInsideWarning;

  /// No description provided for @onboardingAppSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Open-source Nostr navigation'**
  String get onboardingAppSubtitle;

  /// No description provided for @onboardingWelcomeTitle.
  ///
  /// In en, this message translates to:
  /// **'Welcome'**
  String get onboardingWelcomeTitle;

  /// No description provided for @onboardingWelcomeBody.
  ///
  /// In en, this message translates to:
  /// **'Everything your navigation app needs — without giving up your privacy.'**
  String get onboardingWelcomeBody;

  /// No description provided for @onboardingFeatureNav.
  ///
  /// In en, this message translates to:
  /// **'Turn-by-turn GPS navigation'**
  String get onboardingFeatureNav;

  /// No description provided for @onboardingFeatureNostr.
  ///
  /// In en, this message translates to:
  /// **'Nostr road events (speed cameras, hazards, traffic)'**
  String get onboardingFeatureNostr;

  /// No description provided for @onboardingFeatureLightning.
  ///
  /// In en, this message translates to:
  /// **'Lightning Network tips for event reporters'**
  String get onboardingFeatureLightning;

  /// No description provided for @onboardingFeatureVoice.
  ///
  /// In en, this message translates to:
  /// **'On-device AI voice guidance (Kokoro-82M)'**
  String get onboardingFeatureVoice;

  /// No description provided for @onboardingFeaturePrivacy.
  ///
  /// In en, this message translates to:
  /// **'No account required — no tracking, ever'**
  String get onboardingFeaturePrivacy;

  /// No description provided for @onboardingGetStarted.
  ///
  /// In en, this message translates to:
  /// **'Get started'**
  String get onboardingGetStarted;

  /// No description provided for @onboardingNostrTitle.
  ///
  /// In en, this message translates to:
  /// **'Nostr Identity'**
  String get onboardingNostrTitle;

  /// No description provided for @onboardingNostrSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Optional — connect to report road events, confirm alerts and earn Lightning tips.'**
  String get onboardingNostrSubtitle;

  /// No description provided for @onboardingNostrConnected.
  ///
  /// In en, this message translates to:
  /// **'Connected'**
  String get onboardingNostrConnected;

  /// No description provided for @onboardingAmberTitle.
  ///
  /// In en, this message translates to:
  /// **'Amber — NIP-55 (recommended)'**
  String get onboardingAmberTitle;

  /// No description provided for @onboardingAmberSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Connect with the Amber signer app. Your private key never leaves Amber.'**
  String get onboardingAmberSubtitle;

  /// No description provided for @onboardingNsecTitle.
  ///
  /// In en, this message translates to:
  /// **'nsec key'**
  String get onboardingNsecTitle;

  /// No description provided for @onboardingNsecSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Paste your private key. Stored in the Android Keystore.'**
  String get onboardingNsecSubtitle;

  /// No description provided for @onboardingNsecError.
  ///
  /// In en, this message translates to:
  /// **'Invalid nsec key — please check and try again.'**
  String get onboardingNsecError;

  /// No description provided for @onboardingSkip.
  ///
  /// In en, this message translates to:
  /// **'Skip for now'**
  String get onboardingSkip;

  /// No description provided for @onboardingContinue.
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get onboardingContinue;

  /// No description provided for @onboardingEnterNsec.
  ///
  /// In en, this message translates to:
  /// **'Enter nsec key'**
  String get onboardingEnterNsec;

  /// No description provided for @onboardingSetupTitle.
  ///
  /// In en, this message translates to:
  /// **'Set up Roadstr'**
  String get onboardingSetupTitle;

  /// No description provided for @onboardingSetupSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Configure location access and optional voice guidance.'**
  String get onboardingSetupSubtitle;

  /// No description provided for @onboardingLocationTitle.
  ///
  /// In en, this message translates to:
  /// **'Location'**
  String get onboardingLocationTitle;

  /// No description provided for @onboardingLocationGranted.
  ///
  /// In en, this message translates to:
  /// **'Location access granted'**
  String get onboardingLocationGranted;

  /// No description provided for @onboardingLocationRequired.
  ///
  /// In en, this message translates to:
  /// **'Required for GPS navigation'**
  String get onboardingLocationRequired;

  /// No description provided for @onboardingGrantButton.
  ///
  /// In en, this message translates to:
  /// **'Grant'**
  String get onboardingGrantButton;

  /// No description provided for @onboardingGrapheneTitle.
  ///
  /// In en, this message translates to:
  /// **'GrapheneOS users'**
  String get onboardingGrapheneTitle;

  /// No description provided for @onboardingGrapheneBody.
  ///
  /// In en, this message translates to:
  /// **'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.'**
  String get onboardingGrapheneBody;

  /// No description provided for @onboardingVoiceTitle.
  ///
  /// In en, this message translates to:
  /// **'AI voice guidance (optional)'**
  String get onboardingVoiceTitle;

  /// No description provided for @onboardingVoiceReady.
  ///
  /// In en, this message translates to:
  /// **'Kokoro voice model ready'**
  String get onboardingVoiceReady;

  /// No description provided for @onboardingVoiceDownloading.
  ///
  /// In en, this message translates to:
  /// **'Downloading voice model…'**
  String get onboardingVoiceDownloading;

  /// No description provided for @onboardingVoiceNotDownloaded.
  ///
  /// In en, this message translates to:
  /// **'Download the 82 MB Kokoro AI model for on-device voice'**
  String get onboardingVoiceNotDownloaded;

  /// No description provided for @onboardingVoiceChecking.
  ///
  /// In en, this message translates to:
  /// **'Checking model status…'**
  String get onboardingVoiceChecking;

  /// No description provided for @onboardingDownloadButton.
  ///
  /// In en, this message translates to:
  /// **'Download'**
  String get onboardingDownloadButton;

  /// No description provided for @onboardingVoiceLaterHint.
  ///
  /// In en, this message translates to:
  /// **'You can also download the voice model later from\nSettings → Navigation voice.'**
  String get onboardingVoiceLaterHint;

  /// No description provided for @onboardingReadyTitle.
  ///
  /// In en, this message translates to:
  /// **'You\'re ready to navigate!'**
  String get onboardingReadyTitle;

  /// No description provided for @onboardingReadyBody.
  ///
  /// In en, this message translates to:
  /// **'Roadstr will now open the map.\nYou can configure everything else in Settings.'**
  String get onboardingReadyBody;

  /// No description provided for @onboardingLetsGo.
  ///
  /// In en, this message translates to:
  /// **'Let\'s go!'**
  String get onboardingLetsGo;

  /// No description provided for @onboardingProfileLoading.
  ///
  /// In en, this message translates to:
  /// **'Loading profile…'**
  String get onboardingProfileLoading;

  /// No description provided for @onboardingNsecHint.
  ///
  /// In en, this message translates to:
  /// **'nsec1…'**
  String get onboardingNsecHint;

  /// No description provided for @kokoroVoiceGenderTitle.
  ///
  /// In en, this message translates to:
  /// **'Voice'**
  String get kokoroVoiceGenderTitle;

  /// No description provided for @kokoroVoiceFemale.
  ///
  /// In en, this message translates to:
  /// **'Female'**
  String get kokoroVoiceFemale;

  /// No description provided for @kokoroVoiceMale.
  ///
  /// In en, this message translates to:
  /// **'Male'**
  String get kokoroVoiceMale;

  /// No description provided for @kokoroVoiceGenderUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Male voice not available for this language'**
  String get kokoroVoiceGenderUnavailable;

  /// No description provided for @kokoroSpeedTitle.
  ///
  /// In en, this message translates to:
  /// **'Speech speed'**
  String get kokoroSpeedTitle;

  /// No description provided for @onboardingFavoritesSyncNotice.
  ///
  /// In en, this message translates to:
  /// **'Optional: your saved favorites can sync across your devices through the Nostr relays, end-to-end encrypted (NIP-44) with your own key — relays only ever see ciphertext, and nobody but you can read the contents. Enable it anytime from Settings.'**
  String get onboardingFavoritesSyncNotice;

  /// No description provided for @parkingSaveHere.
  ///
  /// In en, this message translates to:
  /// **'Save parking here'**
  String get parkingSaveHere;

  /// No description provided for @parkingMarkerTitle.
  ///
  /// In en, this message translates to:
  /// **'Parking spot'**
  String get parkingMarkerTitle;

  /// No description provided for @parkingNavigateHere.
  ///
  /// In en, this message translates to:
  /// **'Navigate to parking'**
  String get parkingNavigateHere;

  /// No description provided for @parkingRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove parking'**
  String get parkingRemove;

  /// No description provided for @parkingSavedSnack.
  ///
  /// In en, this message translates to:
  /// **'Parking spot saved'**
  String get parkingSavedSnack;

  /// No description provided for @parkingRemovedSnack.
  ///
  /// In en, this message translates to:
  /// **'Parking spot removed'**
  String get parkingRemovedSnack;

  /// No description provided for @exportFavoritesTitle.
  ///
  /// In en, this message translates to:
  /// **'Export favorites'**
  String get exportFavoritesTitle;

  /// No description provided for @exportFavoritesDesc.
  ///
  /// In en, this message translates to:
  /// **'Save your favorite places to a JSON file you can back up or move to another device.'**
  String get exportFavoritesDesc;

  /// No description provided for @exportEncryptToggle.
  ///
  /// In en, this message translates to:
  /// **'Encrypt with a password'**
  String get exportEncryptToggle;

  /// No description provided for @exportPasswordHint.
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get exportPasswordHint;

  /// No description provided for @exportButton.
  ///
  /// In en, this message translates to:
  /// **'Export'**
  String get exportButton;

  /// No description provided for @exportSuccessSnack.
  ///
  /// In en, this message translates to:
  /// **'Favorites exported'**
  String get exportSuccessSnack;

  /// No description provided for @exportFailedSnack.
  ///
  /// In en, this message translates to:
  /// **'Export failed'**
  String get exportFailedSnack;

  /// No description provided for @importFavoritesTitle.
  ///
  /// In en, this message translates to:
  /// **'Import favorites'**
  String get importFavoritesTitle;

  /// No description provided for @importButton.
  ///
  /// In en, this message translates to:
  /// **'Import'**
  String get importButton;

  /// No description provided for @importPasswordPrompt.
  ///
  /// In en, this message translates to:
  /// **'This file is encrypted — enter the password'**
  String get importPasswordPrompt;

  /// No description provided for @importSuccessSnack.
  ///
  /// In en, this message translates to:
  /// **'{n} favorites imported'**
  String importSuccessSnack(int n);

  /// No description provided for @importFailedSnack.
  ///
  /// In en, this message translates to:
  /// **'Import failed — check the file and password'**
  String get importFailedSnack;

  /// No description provided for @syncFavoritesTitle.
  ///
  /// In en, this message translates to:
  /// **'Sync favorites'**
  String get syncFavoritesTitle;

  /// No description provided for @syncFavoritesDesc.
  ///
  /// In en, this message translates to:
  /// **'Publish your favorites, end-to-end encrypted, to your Nostr relays so they follow you across devices. Relays only ever see ciphertext — nobody but you can read the contents.'**
  String get syncFavoritesDesc;

  /// No description provided for @syncFavoritesEnable.
  ///
  /// In en, this message translates to:
  /// **'Enable sync'**
  String get syncFavoritesEnable;

  /// No description provided for @syncNowButton.
  ///
  /// In en, this message translates to:
  /// **'Push to Nostr'**
  String get syncNowButton;

  /// No description provided for @syncPullButton.
  ///
  /// In en, this message translates to:
  /// **'Pull from Nostr'**
  String get syncPullButton;

  /// No description provided for @syncPushingStatus.
  ///
  /// In en, this message translates to:
  /// **'Publishing…'**
  String get syncPushingStatus;

  /// No description provided for @syncPullingStatus.
  ///
  /// In en, this message translates to:
  /// **'Fetching…'**
  String get syncPullingStatus;

  /// No description provided for @syncSuccessSnack.
  ///
  /// In en, this message translates to:
  /// **'Favorites synced'**
  String get syncSuccessSnack;

  /// No description provided for @syncFailedSnack.
  ///
  /// In en, this message translates to:
  /// **'Sync failed'**
  String get syncFailedSnack;

  /// No description provided for @syncNotAvailableAmber.
  ///
  /// In en, this message translates to:
  /// **'Encrypted sync isn\'t available with Amber sign-in yet'**
  String get syncNotAvailableAmber;

  /// No description provided for @syncLastSyncLabel.
  ///
  /// In en, this message translates to:
  /// **'Last synced: {when}'**
  String syncLastSyncLabel(String when);

  /// No description provided for @syncNoIdentity.
  ///
  /// In en, this message translates to:
  /// **'Sign in with Nostr (Settings → Profile) to enable sync'**
  String get syncNoIdentity;

  /// No description provided for @syncPullConfirmTitle.
  ///
  /// In en, this message translates to:
  /// **'Replace local favorites?'**
  String get syncPullConfirmTitle;

  /// No description provided for @syncPullConfirmBody.
  ///
  /// In en, this message translates to:
  /// **'This will merge favorites fetched from Nostr with the ones already on this device.'**
  String get syncPullConfirmBody;

  /// No description provided for @onboardingVpnNotice.
  ///
  /// In en, this message translates to:
  /// **'For maximum privacy — road reports are propagated through the Nostr network — using a VPN is recommended. Mullvad, payable in Bitcoin, is the recommended choice.'**
  String get onboardingVpnNotice;

  /// No description provided for @onboardingGrapheneAlwaysAllow.
  ///
  /// In en, this message translates to:
  /// **'For reliable operation, set the app\'s location permission to \"Allow all the time\", not only while the app is in use.'**
  String get onboardingGrapheneAlwaysAllow;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) => <String>[
        'bg',
        'cs',
        'da',
        'de',
        'el',
        'en',
        'es',
        'et',
        'fi',
        'fr',
        'ga',
        'hr',
        'hu',
        'it',
        'ja',
        'lt',
        'lv',
        'mt',
        'nl',
        'pl',
        'pt',
        'ro',
        'ru',
        'sk',
        'sl',
        'sv',
        'zh'
      ].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'bg':
      return AppLocalizationsBg();
    case 'cs':
      return AppLocalizationsCs();
    case 'da':
      return AppLocalizationsDa();
    case 'de':
      return AppLocalizationsDe();
    case 'el':
      return AppLocalizationsEl();
    case 'en':
      return AppLocalizationsEn();
    case 'es':
      return AppLocalizationsEs();
    case 'et':
      return AppLocalizationsEt();
    case 'fi':
      return AppLocalizationsFi();
    case 'fr':
      return AppLocalizationsFr();
    case 'ga':
      return AppLocalizationsGa();
    case 'hr':
      return AppLocalizationsHr();
    case 'hu':
      return AppLocalizationsHu();
    case 'it':
      return AppLocalizationsIt();
    case 'ja':
      return AppLocalizationsJa();
    case 'lt':
      return AppLocalizationsLt();
    case 'lv':
      return AppLocalizationsLv();
    case 'mt':
      return AppLocalizationsMt();
    case 'nl':
      return AppLocalizationsNl();
    case 'pl':
      return AppLocalizationsPl();
    case 'pt':
      return AppLocalizationsPt();
    case 'ro':
      return AppLocalizationsRo();
    case 'ru':
      return AppLocalizationsRu();
    case 'sk':
      return AppLocalizationsSk();
    case 'sl':
      return AppLocalizationsSl();
    case 'sv':
      return AppLocalizationsSv();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
      'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
      'an issue with the localizations generation tool. Please file an issue '
      'on GitHub with a reproducible sample app and the gen-l10n configuration '
      'that was used.');
}
