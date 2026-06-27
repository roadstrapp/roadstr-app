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
  String get sectionPrivacy => 'Prywatność';

  @override
  String get sectionInfo => 'Info';

  @override
  String get sectionLanguage => 'Język';

  @override
  String get themeLightNostr => 'Jasny · Nostr Fiolet';

  @override
  String get themeLightBitcoin => 'Jasny · Bitcoin Pomarańcz';

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
  String get privacyMode => 'Tryb prywatności';

  @override
  String get privacyModeDescription =>
      'Nie wysyłaj anonimowych danych telemetrycznych';

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
}
