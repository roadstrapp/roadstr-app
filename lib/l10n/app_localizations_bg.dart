// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Bulgarian (`bg`).
class AppLocalizationsBg extends AppLocalizations {
  AppLocalizationsBg([String locale = 'bg']) : super(locale);

  @override
  String get searchHint => 'Къде искате да отидете?';

  @override
  String get calculatingRoute => 'Изчисляване на маршрут…';

  @override
  String get routeNotFoundTitle => 'Маршрутът не е намерен';

  @override
  String get noRouteFound => 'Не е намерен маршрут. Проверете връзката си.';

  @override
  String get emptyServerResponse =>
      'Празен отговор от сървъра. Проверете връзката си.';

  @override
  String routeError(String error) {
    return 'Грешка при изчисляване на маршрута: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS не е наличен — Настройки → Приложение → Roadstr → Разрешения → Местоположение';

  @override
  String get acquiringGps => 'Получаване на GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Сървърът GraphHopper не е конфигуриран — използва се OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'API ключът на GraphHopper не е конфигуриран — използва се OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'API ключът на OpenRouteService не е конфигуриран — използва се OSRM';

  @override
  String get chooseRoute => 'Избор на маршрут';

  @override
  String routeOptionsCount(int count) {
    return '$count опции';
  }

  @override
  String get cancel => 'Отказ';

  @override
  String get startNavigation => 'Стартиране на навигацията';

  @override
  String get fastestRoute => 'Най-бърз';

  @override
  String get now => 'Сега';

  @override
  String get history => 'История';

  @override
  String get clearHistory => 'Изчисти';

  @override
  String get selectedPosition => 'Избрана позиция';

  @override
  String get bottomBarProfile => 'Профил';

  @override
  String get bottomBarMenu => 'Меню';

  @override
  String get settingsTitle => 'Настройки';

  @override
  String get sectionTheme => 'Тема';

  @override
  String get sectionMap => 'Карта';

  @override
  String get sectionPrivacy => 'Поверителност';

  @override
  String get sectionInfo => 'Инфо';

  @override
  String get sectionLanguage => 'Език';

  @override
  String get themeLightNostr => 'Светла · Nostr Виолетово';

  @override
  String get themeLightBitcoin => 'Светла · Bitcoin Оранжево';

  @override
  String get langSystem => 'Системно по подразбиране';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Дръж екрана включен';

  @override
  String get keepScreenOnDescription =>
      'Предотвратява преминаването в режим на сън по време на навигация';

  @override
  String get rotateMap => 'Картата следва посоката';

  @override
  String get rotateMapDescription =>
      'Картата се завърта според посоката на движение';

  @override
  String get mapTileUrlLabel => 'URL на картографски плочки';

  @override
  String get routingProviderLabel => 'Доставчик на маршрутизиране';

  @override
  String get osrmProvider => 'OSRM (публичен, без ключ)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (локален/частен)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API ключ)';

  @override
  String get openrouteProvider => 'OpenRouteService (API ключ)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'API ключ на GraphHopper (по избор)';

  @override
  String get verify => 'Провери';

  @override
  String get graphhopperServerUrlRequired =>
      'Въведете URL на сървъра преди проверката.';

  @override
  String get successTitle => 'Успех';

  @override
  String get graphhopperServerReachable => 'Сървърът GraphHopper е достъпен';

  @override
  String get errorTitle => 'Грешка';

  @override
  String get close => 'Затвори';

  @override
  String get privacyMode => 'Режим на поверителност';

  @override
  String get privacyModeDescription =>
      'Не изпращайте анонимни телеметрични данни';

  @override
  String get infoVersion => 'Версия';

  @override
  String get infoProtocol => 'Протокол';

  @override
  String get infoMaps => 'Карти';

  @override
  String get infoRouting => 'Маршрутизиране';

  @override
  String get infoSource => 'Източник';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted =>
      'GraphHopper (самостоятелно хостван)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (облак)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Профил';

  @override
  String get notConnected => 'Не е свързан';

  @override
  String get loginWithNostrTitle => 'ВЛЕЗТЕ С NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Личният ключ никога не напуска устройството ви · Препоръчително';

  @override
  String get nsecLoginOption => 'Вмъкнете nsec';

  @override
  String get nsecLoginDescription =>
      'Личният ключ се съхранява локално · По-малко сигурно';

  @override
  String get connectedViaAmber => 'Свързан чрез Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Свързан чрез nsec';

  @override
  String get publicKeyLabel => 'ПУБЛИЧЕН КЛЮЧ';

  @override
  String get npubCopiedToClipboard => 'npub копиран в клипборда';

  @override
  String get logoutTitle => 'Изключване';

  @override
  String get logoutConfirmation =>
      'Да се премахнат ли идентификационните данни на Nostr от това устройство?';

  @override
  String get logoutButton => 'Изключване';

  @override
  String get nostrIdentityInfo =>
      'С Nostr самоличност можете да публикувате предупреждения за трафик, доклади и забележителности по децентрализиран начин в мрежата Nostr, без централни сървъри.';

  @override
  String get warningTitle => 'Предупреждение';

  @override
  String get nsecWarning =>
      'Предстои да въведете личния си ключ на Nostr директно в приложение. Всеки с физически или отдалечен достъп до устройството ви може да го прочете и трайно да поеме контрол над вашата Nostr самоличност.';

  @override
  String get amberSecureMethodHint =>
      '✦  Безопасният метод е Amber (NIP-55): nsec никога не напуска трезора на подписвача на приложението.';

  @override
  String get nsecRiskAcknowledgment =>
      'Разбирам риска и искам да продължа въпреки това';

  @override
  String get continueButton => 'Продължи';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber е подписвач на приложения за Android, съвместим с NIP-55. Вашият личен ключ остава изолиран в Amber и никога не се споделя.';

  @override
  String get requestKeyFromAmber => 'Поискайте публичен ключ от Amber';

  @override
  String get amberNotFound =>
      'Amber не е намерен. Инсталирайте го от Play Store или въведете ръчно вашия npub.';

  @override
  String get waitingForAmberResponse => 'Изчакване на отговор от Amber…';

  @override
  String get pasteNpubManually => 'Поставете вашия npub ръчно:';

  @override
  String get confirmNpub => 'Потвърдете npub';

  @override
  String get enterNsecTitle => 'Вмъкнете nsec';

  @override
  String get loginButton => 'Вход';

  @override
  String get invalidNpubTitle => 'Невалиден npub';

  @override
  String get invalidNsecTitle => 'Невалиден nsec';

  @override
  String get invalidNpubMessage =>
      'Уверете се, че сте поставили правилния npub.';

  @override
  String get invalidNsecMessage =>
      'Уверете се, че сте поставили правилния nsec.';

  @override
  String get amberResponseError => 'Грешка в отговора на Amber';

  @override
  String get ok => 'OK';

  @override
  String get or => 'или';

  @override
  String get gpsNotActiveTitle => 'GPS не е активен';

  @override
  String get gpsDisabledMessage =>
      'Активирайте GPS в настройките на устройството, за да получите местоположението си и да използвате навигацията.';

  @override
  String get openSettings => 'Настройки';

  @override
  String get myLocation => 'Моето местоположение';

  @override
  String get loginToReport =>
      'Влезте с Nostr (секция Профил), за да докладвате събития';

  @override
  String get navigateHere => 'Навигирайте тук';

  @override
  String get reportEventHere => 'Докладвайте събитие тук';

  @override
  String get zapSendSats => 'Zap ⚡ (изпратете sats)';

  @override
  String get sendZap => 'Изпратете Zap';

  @override
  String get chooseAmountSats => 'Изберете сума в satoshi (sats):';

  @override
  String get customAmount => 'Персонализирана сума…';

  @override
  String get zapSending => 'Изпращане…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Извличане на Lightning адрес…';

  @override
  String get noLightningAddress => 'Този репортер няма Lightning адрес';

  @override
  String get requestingInvoice => 'Заявка за фактура…';

  @override
  String get lnurlUnavailable => 'LNURL не е наличен';

  @override
  String get invoiceFailed => 'Неуспешно генериране на фактура';

  @override
  String get openingWallet => 'Отваряне на портфейл…';

  @override
  String get payingViaNwc => 'Плащане чрез NWC…';

  @override
  String get noLightningWallet => 'Не е намерен Lightning портфейл';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats изпратени!';
  }

  @override
  String get stillThere => 'Все още там';

  @override
  String get notThereAnymore => 'Вече го няма';

  @override
  String get loginToConfirm => 'Влезте с Nostr, за да потвърдите или оспорите';

  @override
  String get reportAnEvent => 'Докладване на събитие';

  @override
  String get optionalComment => 'Незадължителен коментар…';

  @override
  String get publish => 'Публикуване';

  @override
  String get publishing => 'Публикуване…';

  @override
  String get reportPublished => 'Докладът е публикуван ✓';

  @override
  String get myReports => 'МОИТЕ ДОКЛАДИ';

  @override
  String get noReportsYet => 'Няма публикувани доклади';

  @override
  String get zapBalance => 'Zap баланс';

  @override
  String get satoshiFromReports => 'Satoshi получени от вашите доклади';

  @override
  String get reputationHigh => 'Висока';

  @override
  String get reputationMedium => 'Средна';

  @override
  String get reputationLow => 'Ниска';

  @override
  String reputationLabel(String level) {
    return 'Репутация $level';
  }

  @override
  String reliability(int pct) {
    return 'Надеждност: $pct%';
  }

  @override
  String get confirmedLabel => 'Потвърдено';

  @override
  String get removedLabel => 'Премахнато';

  @override
  String get positionLabel => 'Позиция';

  @override
  String get loadingLabel => 'Зареждане…';

  @override
  String get sectionWebSearch => 'Уеб търсене';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Поставете вашия NWC URI (Alby Hub, Mutiny, Cashu…), за да плащате Zap директно от приложението.';

  @override
  String get searchEngineQwantDesc =>
      'Европейска, с приоритет на поверителността. Без проследяване, без рекламни профили. Препоръчително.';

  @override
  String get searchEngineBraveDesc =>
      'Независим индекс, с отворен код. Без зависимост от Google или Bing. Нулево профилиране.';

  @override
  String get searchEngineDdgDesc =>
      'Фокусирана върху поверителността и популярна. Резултатите частично от Bing — имайте предвид.';

  @override
  String get searchEngineStartpageDesc =>
      'Резултати от Google, без да предавате данните си на Google. Разумен компромис.';

  @override
  String get searchEngineGoogleDesc =>
      'Много ефективна. Но помнете: Google ви познава по-добре от майка ви и продава всичко на рекламодатели. Вашият избор. 🍪';

  @override
  String get categoryPolice => 'Полиция';

  @override
  String get categorySpeedCamera => 'Камера за скорост';

  @override
  String get categoryTrafficJam => 'Задръстване';

  @override
  String get categoryAccident => 'Катастрофа';

  @override
  String get categoryRoadClosure => 'Затворен път';

  @override
  String get categoryConstruction => 'Строителство';

  @override
  String get categoryHazard => 'Опасност';

  @override
  String get categoryRoadCondition => 'Състояние на пътя';

  @override
  String get categoryPothole => 'Дупка в пътя';

  @override
  String get categoryFog => 'Мъгла';

  @override
  String get categoryIce => 'Лед';

  @override
  String get categoryAnimal => 'Животно';

  @override
  String get categoryOther => 'Друго';

  @override
  String get dateTimeLabel => 'Дата / час';

  @override
  String minutesAgo(int count) {
    return 'преди $count мин';
  }

  @override
  String hoursAgo(int count) {
    return 'преди $countч';
  }

  @override
  String daysAgo(int count) {
    return 'преди $countд';
  }

  @override
  String get sectionFavorites => 'Запазени места';

  @override
  String get addFavorite => 'Добави място';

  @override
  String get favoriteLabelHint => 'Име (напр. Вкъщи, Офис)';

  @override
  String get favoriteAddressHint => 'Адрес';

  @override
  String get favoriteGeocodingError =>
      'Адресът не е намерен. Опитай по-точен адрес.';
}
