// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Polish (`pl`).
class AppLocalizationsPl extends AppLocalizations {
  AppLocalizationsPl([String locale = 'pl']) : super(locale);

  @override
  String get searchHint => 'Dokąd chcesz jechać?';

  @override
  String get calculatingRoute => 'Obliczanie trasy…';

  @override
  String get routeNotFoundTitle => 'Nie znaleziono trasy';

  @override
  String get noRouteFound => 'Nie znaleziono trasy. Sprawdź połączenie.';

  @override
  String get emptyServerResponse =>
      'Pusta odpowiedź serwera. Sprawdź połączenie.';

  @override
  String routeError(String error) {
    return 'Błąd obliczania trasy: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS niedostępny — Ustawienia → Aplikacja → Roadstr → Uprawnienia → Lokalizacja';

  @override
  String get acquiringGps => 'Pobieranie GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Serwer GraphHopper nie skonfigurowany — używanie OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'Klucz API GraphHopper nie skonfigurowany — używanie OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'Klucz API OpenRouteService nie skonfigurowany — używanie OSRM';

  @override
  String get chooseRoute => 'Wybierz trasę';

  @override
  String routeOptionsCount(int count) {
    return '$count opcji';
  }

  @override
  String get cancel => 'Anuluj';

  @override
  String get startNavigation => 'Rozpocznij nawigację';

  @override
  String get fastestRoute => 'Najszybsza';

  @override
  String get now => 'Teraz';

  @override
  String get history => 'Historia';

  @override
  String get clearHistory => 'Wyczyść';

  @override
  String get selectedPosition => 'Wybrana pozycja';

  @override
  String get bottomBarProfile => 'Profil';

  @override
  String get bottomBarMenu => 'Menu';

  @override
  String get settingsTitle => 'Ustawienia';

  @override
  String get sectionTheme => 'Motyw';

  @override
  String get sectionMap => 'Mapa';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Język';

  @override
  String get themeLightNostr => 'Jasny · Nostr Fiolet';

  @override
  String get themeLightBitcoin => 'Jasny · Bitcoin Pomarańcz';

  @override
  String get themeDarkNostr => 'Ciemny · Nostr Fiolet';

  @override
  String get themeDarkBitcoin => 'Ciemny · Bitcoin Pomarańcz';

  @override
  String get langSystem => 'Domyślny systemowy';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Utrzymuj ekran włączony';

  @override
  String get keepScreenOnDescription => 'Zapobiega uśpieniu podczas nawigacji';

  @override
  String get rotateMap => 'Mapa podąża za kierunkiem';

  @override
  String get rotateMapDescription =>
      'Mapa obraca się zgodnie z kierunkiem jazdy';

  @override
  String get mapTileUrlLabel => 'URL kafelków mapy';

  @override
  String get routingProviderLabel => 'Dostawca trasowania';

  @override
  String get osrmProvider => 'OSRM (publiczny, bez klucza)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (lokalny/prywatny)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (klucz API)';

  @override
  String get openrouteProvider => 'OpenRouteService (klucz API)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'Klucz API GraphHopper (opcjonalny)';

  @override
  String get verify => 'Weryfikuj';

  @override
  String get graphhopperServerUrlRequired =>
      'Wprowadź adres URL serwera przed weryfikacją.';

  @override
  String get successTitle => 'Sukces';

  @override
  String get graphhopperServerReachable => 'Serwer GraphHopper dostępny';

  @override
  String get errorTitle => 'Błąd';

  @override
  String get close => 'Zamknij';

  @override
  String get infoVersion => 'Wersja';

  @override
  String get infoProtocol => 'Protokół';

  @override
  String get infoMaps => 'Mapy';

  @override
  String get infoRouting => 'Trasowanie';

  @override
  String get infoSource => 'Źródło';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted =>
      'GraphHopper (samodzielnie hostowany)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (chmura)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Profil';

  @override
  String get notConnected => 'Niepołączony';

  @override
  String get loginWithNostrTitle => 'ZALOGUJ SIĘ Z NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Klucz prywatny nigdy nie opuszcza urządzenia · Zalecane';

  @override
  String get nsecLoginOption => 'Wstaw nsec';

  @override
  String get nsecLoginDescription =>
      'Klucz prywatny przechowywany lokalnie · Mniej bezpieczny';

  @override
  String get connectedViaAmber => 'Połączono przez Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Połączono przez nsec';

  @override
  String get publicKeyLabel => 'KLUCZ PUBLICZNY';

  @override
  String get npubCopiedToClipboard => 'npub skopiowany do schowka';

  @override
  String get logoutTitle => 'Rozłącz';

  @override
  String get logoutConfirmation =>
      'Usunąć dane uwierzytelniające Nostr z tego urządzenia?';

  @override
  String get logoutButton => 'Rozłącz';

  @override
  String get nostrIdentityInfo =>
      'Z tożsamością Nostr możesz publikować alerty drogowe, raporty i punkty zainteresowania w sposób zdecentralizowany w sieci Nostr, bez centralnych serwerów.';

  @override
  String get warningTitle => 'Ostrzeżenie';

  @override
  String get nsecWarning =>
      'Zamierzasz wprowadzić swój prywatny klucz Nostr bezpośrednio do aplikacji. Każda osoba z fizycznym lub zdalnym dostępem do urządzenia może go odczytać i trwale przejąć kontrolę nad Twoją tożsamością Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  Bezpieczną metodą jest Amber (NIP-55): nsec nigdy nie opuszcza skarbca podpisującego aplikację.';

  @override
  String get nsecRiskAcknowledgment =>
      'Rozumiem ryzyko i chcę kontynuować mimo to';

  @override
  String get continueButton => 'Kontynuuj';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber to zgodny z NIP-55 podpisujący aplikację Android. Twój klucz prywatny pozostaje izolowany wewnątrz Ambera i nigdy nie jest udostępniany.';

  @override
  String get requestKeyFromAmber => 'Poproś o klucz publiczny z Ambera';

  @override
  String get amberNotFound =>
      'Nie znaleziono Ambera. Zainstaluj go ze Sklepu Play lub wprowadź swój npub ręcznie.';

  @override
  String get waitingForAmberResponse => 'Oczekiwanie na odpowiedź Ambera…';

  @override
  String get pasteNpubManually => 'Wklej swój npub ręcznie:';

  @override
  String get confirmNpub => 'Potwierdź npub';

  @override
  String get enterNsecTitle => 'Wstaw nsec';

  @override
  String get loginButton => 'Zaloguj';

  @override
  String get invalidNpubTitle => 'Nieprawidłowy npub';

  @override
  String get invalidNsecTitle => 'Nieprawidłowy nsec';

  @override
  String get invalidNpubMessage => 'Upewnij się, że wklejono właściwy npub.';

  @override
  String get invalidNsecMessage => 'Upewnij się, że wklejono właściwy nsec.';

  @override
  String get amberResponseError => 'Błąd odpowiedzi Ambera';

  @override
  String get ok => 'OK';

  @override
  String get or => 'lub';

  @override
  String get gpsNotActiveTitle => 'GPS nieaktywny';

  @override
  String get gpsDisabledMessage =>
      'Włącz GPS w ustawieniach urządzenia, aby uzyskać lokalizację i używać nawigacji.';

  @override
  String get openSettings => 'Ustawienia';

  @override
  String get myLocation => 'Moja lokalizacja';

  @override
  String get loginToReport =>
      'Zaloguj się z Nostr (sekcja Profil), aby zgłaszać zdarzenia';

  @override
  String get navigateHere => 'Nawiguj tutaj';

  @override
  String get reportEventHere => 'Zgłoś zdarzenie tutaj';

  @override
  String get zapSendSats => 'Zap ⚡ (wyślij saty)';

  @override
  String get sendZap => 'Wyślij Zap';

  @override
  String get chooseAmountSats => 'Wybierz kwotę w satoshi (sats):';

  @override
  String get customAmount => 'Własna kwota…';

  @override
  String get zapSending => 'Wysyłanie…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Pobieranie adresu Lightning…';

  @override
  String get noLightningAddress => 'Ten zgłaszający nie ma adresu Lightning';

  @override
  String get requestingInvoice => 'Żądanie faktury…';

  @override
  String get lnurlUnavailable => 'LNURL niedostępny';

  @override
  String get invoiceFailed => 'Nie udało się wygenerować faktury';

  @override
  String get openingWallet => 'Otwieranie portfela…';

  @override
  String get payingViaNwc => 'Płatność przez NWC…';

  @override
  String get noLightningWallet => 'Nie znaleziono portfela Lightning';

  @override
  String zapSent(int sats) {
    return '⚡ Wysłano $sats satów!';
  }

  @override
  String get stillThere => 'Nadal tam';

  @override
  String get notThereAnymore => 'Już nie ma';

  @override
  String get loginToConfirm =>
      'Zaloguj się z Nostr, aby potwierdzić lub zakwestionować';

  @override
  String get reportAnEvent => 'Zgłoś zdarzenie';

  @override
  String get optionalComment => 'Opcjonalny komentarz…';

  @override
  String get publish => 'Opublikuj';

  @override
  String get publishing => 'Publikowanie…';

  @override
  String get reportPublished => 'Raport opublikowany ✓';

  @override
  String get myReports => 'MOJE RAPORTY';

  @override
  String get noReportsYet => 'Brak opublikowanych raportów';

  @override
  String get zapBalance => 'Saldo Zap';

  @override
  String get satoshiFromReports => 'Satoshi otrzymane z Twoich raportów';

  @override
  String get reputationHigh => 'Wysoka';

  @override
  String get reputationMedium => 'Średnia';

  @override
  String get reputationLow => 'Niska';

  @override
  String reputationLabel(String level) {
    return 'Reputacja $level';
  }

  @override
  String reliability(int pct) {
    return 'Niezawodność: $pct%';
  }

  @override
  String get confirmedLabel => 'Potwierdzone';

  @override
  String get removedLabel => 'Usunięte';

  @override
  String get positionLabel => 'Pozycja';

  @override
  String get loadingLabel => 'Ładowanie…';

  @override
  String get sectionWebSearch => 'Wyszukiwanie w sieci';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Wklej swój URI NWC (Alby Hub, Mutiny, Cashu…), aby płacić Zapy bezpośrednio z aplikacji.';

  @override
  String get searchEngineQwantDesc =>
      'Europejska, z prywatnością na pierwszym miejscu. Brak śledzenia, brak profili reklamowych. Zalecana.';

  @override
  String get searchEngineBraveDesc =>
      'Niezależny indeks, open-source. Brak zależności od Google czy Bing. Zero profilowania.';

  @override
  String get searchEngineDdgDesc =>
      'Zorientowany na prywatność i popularny. Wyniki częściowo z Bing — miej to na uwadze.';

  @override
  String get searchEngineStartpageDesc =>
      'Wyniki Google bez przekazywania danych do Google. Rozsądny kompromis.';

  @override
  String get searchEngineGoogleDesc =>
      'Bardzo skuteczny. Ale pamiętaj: Google zna Cię lepiej niż Twoja mama i sprzedaje wszystko reklamodawcom. Twój wybór. 🍪';

  @override
  String get categoryPolice => 'Policja';

  @override
  String get categorySpeedCamera => 'Fotoradar';

  @override
  String get categoryTrafficJam => 'Korek';

  @override
  String get categoryAccident => 'Wypadek';

  @override
  String get categoryRoadClosure => 'Zamknięcie drogi';

  @override
  String get categoryConstruction => 'Roboty drogowe';

  @override
  String get categoryHazard => 'Zagrożenie';

  @override
  String get categoryRoadCondition => 'Stan drogi';

  @override
  String get categoryPothole => 'Dziura w jezdni';

  @override
  String get categoryFog => 'Mgła';

  @override
  String get categoryIce => 'Lód';

  @override
  String get categoryAnimal => 'Zwierzę';

  @override
  String get categoryOther => 'Inne';

  @override
  String get dateTimeLabel => 'Data / godzina';

  @override
  String minutesAgo(int count) {
    return '$count min temu';
  }

  @override
  String hoursAgo(int count) {
    return '${count}g temu';
  }

  @override
  String daysAgo(int count) {
    return '${count}d temu';
  }

  @override
  String get sectionFavorites => 'Zapisane miejsca';

  @override
  String get addFavorite => 'Dodaj miejsce';

  @override
  String get favoriteLabelHint => 'Nazwa (np. Dom, Biuro)';

  @override
  String get favoriteAddressHint => 'Adres';

  @override
  String get favoriteGeocodingError =>
      'Nie znaleziono adresu. Spróbuj bardziej szczegółowego.';

  @override
  String get trafficAlertTitle => 'Nowy ruch na trasie';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category zgłoszony $age na Twojej trasie.\n\nCzy chcesz znaleźć alternatywną trasę?';
  }

  @override
  String get trafficContinue => 'Kontynuuj';

  @override
  String get trafficRecalculate => 'Przelicz trasę';

  @override
  String get navExitTitle => 'Zakończyć nawigację?';

  @override
  String get navExitBody => 'Czy chcesz zatrzymać nawigację i wrócić do mapy?';

  @override
  String get navContinue => 'Kontynuuj nawigację';

  @override
  String get navExit => 'Tak, wyjdź';

  @override
  String get loadingInfo => 'Ładowanie informacji…';

  @override
  String get conditionsOnRoute => 'Warunki na trasie';

  @override
  String get calculateRoute => 'Oblicz trasę';

  @override
  String get sectionNavigationVoice => 'Głos nawigacji';

  @override
  String get voiceGuidance => 'Prowadzenie głosowe';

  @override
  String get voiceGuidanceDesc =>
      'Czytaj instrukcje skrętu na głos podczas nawigacji';

  @override
  String get testVoiceEngine => 'Testuj silnik głosowy';

  @override
  String get testVoiceEngineDesc =>
      'Stuknij, aby sprawdzić silnik TTS i uzyskać instrukcje konfiguracji';

  @override
  String get ttsDialogTitle => 'Brak silnika głosowego';

  @override
  String get ttsDialogBody =>
      'Nie znaleziono działającego silnika Text-to-Speech.\n\nRoadstr opiera się wyłącznie na oprogramowaniu open source — zainstaluj jeden z tych darmowych silników z F-Droid:';

  @override
  String get ttsRhvoiceDesc =>
      'Naturalnie brzmiący głos, ograniczona lista języków';

  @override
  String get ttsEspeakDesc => 'Obsługuje ponad 100 języków, robotyczny głos';

  @override
  String get ttsInstallNote =>
      '⚠️ Po instalacji:\n1. Ustawienia Androida → Ułatwienia dostępu → Synteza mowy\n2. Wybierz właśnie zainstalowany silnik\n3. Pobierz dane głosowe dla swojego języka\n4. Uruchom ponownie całkowicie aplikację Roadstr';

  @override
  String get ttsTestNow => 'Testuj teraz';

  @override
  String get voiceUnsupportedTitle => 'Prowadzenie głosowe niedostępne';

  @override
  String get voiceUnsupportedBody =>
      'Twój język nie jest jeszcze obsługiwany w przypadku głosowych wskazówek nawigacyjnych. Instrukcje nawigacji nadal będą wyświetlane jako tekst na ekranie.';

  @override
  String get kokoroModelTitle => 'Model głosowy (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Nie pobrano · ~82 MB';

  @override
  String get kokoroModelStatusDownloading => 'Pobieranie...';

  @override
  String get kokoroModelStatusReady => 'Model głosowy gotowy';

  @override
  String get kokoroModelDownloadBtn => 'Pobierz';

  @override
  String get kokoroModelSupportedLangs =>
      'Obsługuje: włoski, angielski, hiszpański, francuski, japoński, chiński, portugalski';

  @override
  String get autoDarkMode => 'Automatyczny ciemny motyw';

  @override
  String get autoDarkModeDesc => 'Włącza ciemny motyw o zachodzie słońca';

  @override
  String get settingsImperialUnits => 'Jednostki imperialne';

  @override
  String get settingsImperialUnitsDesc =>
      'Mile i stopy zamiast kilometrów i metrów';

  @override
  String get arrivedTitle => '🎉 Dotarłeś!';

  @override
  String get arrivedBody => 'Dotarłeś do celu.';

  @override
  String get arrivedFeedbackPrompt => 'Jak poszło?';

  @override
  String get feedbackBad => 'Źle';

  @override
  String get feedbackGood => 'Dobrze!';

  @override
  String get feedbackDialogTitle => 'Powiedz nam, co poszło nie tak';

  @override
  String get feedbackHint => 'Opisz problem…';

  @override
  String get feedbackSent => 'Opinia wysłana — dziękujemy! 🙏';

  @override
  String get feedbackSubmit => 'Wyślij';

  @override
  String get transportModeCar => 'Samochód';

  @override
  String get transportModeWalk => 'Pieszo';

  @override
  String etaArrivalLabel(String time) {
    return 'Przyb. $time';
  }

  @override
  String get supportRoadstr => 'Wspieraj Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address skopiowane do schowka';
  }

  @override
  String get disclaimerTitle => 'Ważne powiadomienie';

  @override
  String get disclaimerAccept => 'Przeczytałem/am i akceptuję';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Czytaj na Wikipedii';

  @override
  String searchOnEngine(String engine) {
    return 'Szukaj na $engine';
  }

  @override
  String get plannerFromHint => 'Skąd…';

  @override
  String get plannerToHint => 'Cel…';

  @override
  String departEta(String dep, String arr) {
    return 'Odjazd $dep  →  Przybycie $arr';
  }

  @override
  String get modeCar => 'Auto';

  @override
  String get modeBike => 'Rower';

  @override
  String get modeWalk => 'Pieszo';

  @override
  String windSpeed(String speed) {
    return 'wiatr $speed km/h';
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
  String get weatherClear => 'Pogodnie';

  @override
  String get weatherPartlyCloudy => 'Częściowe zachmurz.';

  @override
  String get weatherCloudy => 'Pochmurno';

  @override
  String get weatherFog => 'Mgła';

  @override
  String get weatherLightRain => 'Lekki deszcz';

  @override
  String get weatherRain => 'Deszcz';

  @override
  String get weatherSnow => 'Śnieg';

  @override
  String get weatherShowers => 'Przelotny deszcz';

  @override
  String get weatherThunderstorm => 'Burza';

  @override
  String get ztlAheadWarning =>
      'Strefa ZTL — trasa prowadzi przez strefę ograniczoną';

  @override
  String get ztlInsideWarning => 'Strefa ograniczonego ruchu';

  @override
  String get onboardingAppSubtitle => 'Nawigacja Nostr open source';

  @override
  String get onboardingWelcomeTitle => 'Witaj';

  @override
  String get onboardingWelcomeBody =>
      'Wszystko, czego potrzebuje aplikacja nawigacyjna — bez rezygnacji z prywatności.';

  @override
  String get onboardingFeatureNav => 'Nawigacja GPS krok po kroku';

  @override
  String get onboardingFeatureNostr =>
      'Zdarzenia drogowe Nostr (radary, zagrożenia, ruch)';

  @override
  String get onboardingFeatureLightning =>
      'Napiwki Lightning Network dla zgłaszających zdarzenia';

  @override
  String get onboardingFeatureVoice =>
      'Lokalne AI prowadzenie głosowe (Kokoro-82M)';

  @override
  String get onboardingFeaturePrivacy =>
      'Nie wymaga konta — bez śledzenia, nigdy';

  @override
  String get onboardingGetStarted => 'Rozpocznij';

  @override
  String get onboardingNostrTitle => 'Tożsamość Nostr';

  @override
  String get onboardingNostrSubtitle =>
      'Opcjonalne — połącz się, aby zgłaszać zdarzenia drogowe, potwierdzać alerty i zarabiać napiwki Lightning.';

  @override
  String get onboardingNostrConnected => 'Połączono';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (zalecane)';

  @override
  String get onboardingAmberSubtitle =>
      'Połącz się z aplikacją podpisującą Amber. Twój klucz prywatny nigdy nie opuszcza Amber.';

  @override
  String get onboardingNsecTitle => 'Klucz nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Wklej swój klucz prywatny. Przechowywany w Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Nieprawidłowy klucz nsec — sprawdź i spróbuj ponownie.';

  @override
  String get onboardingSkip => 'Pomiń na razie';

  @override
  String get onboardingContinue => 'Kontynuuj';

  @override
  String get onboardingEnterNsec => 'Wprowadź klucz nsec';

  @override
  String get onboardingSetupTitle => 'Skonfiguruj Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Skonfiguruj dostęp do lokalizacji i opcjonalne prowadzenie głosowe.';

  @override
  String get onboardingLocationTitle => 'Lokalizacja';

  @override
  String get onboardingLocationGranted => 'Dostęp do lokalizacji przyznany';

  @override
  String get onboardingLocationRequired => 'Wymagane do nawigacji GPS';

  @override
  String get onboardingGrantButton => 'Zezwól';

  @override
  String get onboardingGrapheneTitle => 'Użytkownicy GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Przyznaj Dokładną lokalizację (nie Przybliżoną) i zezwól na dostęp Zawsze (nie tylko podczas użytkowania) w:\nUstawienia → Aplikacje → Roadstr → Uprawnienia → Lokalizacja\n\nZ tylko przybliżoną lub \'tylko podczas użytkowania\' lokalizacją nawigacja GPS straci pozycję w tle.';

  @override
  String get onboardingVoiceTitle => 'Prowadzenie głosowe AI (opcjonalne)';

  @override
  String get onboardingVoiceReady => 'Model głosowy Kokoro gotowy';

  @override
  String get onboardingVoiceDownloading => 'Pobieranie modelu głosowego…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Pobierz 82 MB model AI Kokoro dla lokalnej syntezy mowy';

  @override
  String get onboardingVoiceChecking => 'Sprawdzanie stanu modelu…';

  @override
  String get onboardingDownloadButton => 'Pobierz';

  @override
  String get onboardingVoiceLaterHint =>
      'Możesz też pobrać model głosowy później w\nUstawienia → Głos nawigacji.';

  @override
  String get onboardingReadyTitle => 'Jesteś gotowy do nawigacji!';

  @override
  String get onboardingReadyBody =>
      'Roadstr otworzy teraz mapę.\nWszystko inne możesz skonfigurować w Ustawieniach.';

  @override
  String get onboardingLetsGo => 'Ruszamy!';

  @override
  String get onboardingProfileLoading => 'Ładowanie profilu…';

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
      'Dla maksymalnej prywatności — zgłoszenia są rozsyłane w sieci Nostr — zalecane jest korzystanie z VPN. Mullvad, z płatnością w Bitcoinie, to polecany wybór.';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      'Aby aplikacja działała niezawodnie, ustaw uprawnienie lokalizacji na „Zawsze zezwalaj”, a nie tylko podczas używania aplikacji.';
}
