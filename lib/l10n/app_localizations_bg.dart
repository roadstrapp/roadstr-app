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
  String get sectionInfo => 'Инфо';

  @override
  String get sectionLanguage => 'Език';

  @override
  String get themeLightNostr => 'Светла · Nostr Виолетово';

  @override
  String get themeLightBitcoin => 'Светла · Bitcoin Оранжево';

  @override
  String get themeDarkNostr => 'Тъмна · Nostr Виолетово';

  @override
  String get themeDarkBitcoin => 'Тъмна · Bitcoin Оранжево';

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

  @override
  String get trafficAlertTitle => 'Нов трафик по маршрута';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category съобщено $age по вашия маршрут.\n\nИскате ли да намерите алтернативен маршрут?';
  }

  @override
  String get trafficContinue => 'Продължи';

  @override
  String get trafficRecalculate => 'Преизчисли маршрута';

  @override
  String get navExitTitle => 'Излезте от навигацията?';

  @override
  String get navExitBody =>
      'Искате ли да спрете навигацията и да се върнете към картата?';

  @override
  String get navContinue => 'Продължи навигацията';

  @override
  String get navExit => 'Да, излез';

  @override
  String get loadingInfo => 'Зареждане на информация…';

  @override
  String get conditionsOnRoute => 'Условия по маршрута';

  @override
  String get calculateRoute => 'Изчисли маршрута';

  @override
  String get sectionNavigationVoice => 'Глас за навигация';

  @override
  String get voiceGuidance => 'Гласово упътване';

  @override
  String get voiceGuidanceDesc =>
      'Прочитане на инструкциите за завой на глас по време на навигация';

  @override
  String get testVoiceEngine => 'Тествай гласовия двигател';

  @override
  String get testVoiceEngineDesc =>
      'Докоснете, за да проверите TTS двигателя и да получите инструкции за настройка';

  @override
  String get ttsDialogTitle => 'Липсва гласов двигател';

  @override
  String get ttsDialogBody =>
      'Не е намерен работещ Text-to-Speech двигател.\n\nRoadstr разчита единствено на софтуер с отворен код — инсталирайте един от тези безплатни двигатели от F-Droid:';

  @override
  String get ttsRhvoiceDesc =>
      'Естествено звучащ глас, ограничен списък с езици';

  @override
  String get ttsEspeakDesc => 'Покрива над 100 езика, роботизиран глас';

  @override
  String get ttsInstallNote =>
      '⚠️ След инсталиране:\n1. Настройки на Android → Достъпност → Синтезатор на реч\n2. Изберете току-що инсталирания двигател\n3. Изтеглете гласовите данни за вашия език\n4. Рестартирайте Roadstr напълно';

  @override
  String get ttsTestNow => 'Тествай сега';

  @override
  String get voiceUnsupportedTitle => 'Гласовото упътване не е налично';

  @override
  String get voiceUnsupportedBody =>
      'Вашият език все още не се поддържа за гласови насоки за завой. Инструкциите за навигация ще продължат да се показват като текст на екрана.';

  @override
  String get kokoroModelTitle => 'Гласов модел (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Не е изтеглено · ~82 МБ';

  @override
  String get kokoroModelStatusDownloading => 'Изтегляне...';

  @override
  String get kokoroModelStatusReady => 'Гласовият модел е готов';

  @override
  String get kokoroModelDownloadBtn => 'Изтегли';

  @override
  String get kokoroModelSupportedLangs =>
      'Поддържа: италиански, английски, испански, френски, японски, китайски, португалски';

  @override
  String get autoDarkMode => 'Авто тъмна тема';

  @override
  String get autoDarkModeDesc => 'Активира тъмната тема при залез слънце';

  @override
  String get arrivedTitle => '🎉 Пристигнахте!';

  @override
  String get arrivedBody => 'Достигнахте дестинацията си.';

  @override
  String get arrivedFeedbackPrompt => 'Как мина?';

  @override
  String get feedbackBad => 'Зле';

  @override
  String get feedbackGood => 'Добре!';

  @override
  String get feedbackDialogTitle => 'Кажете ни какво е объркало';

  @override
  String get feedbackHint => 'Опишете проблема…';

  @override
  String get feedbackSent => 'Обратна връзка изпратена — благодаря! 🙏';

  @override
  String get feedbackSubmit => 'Изпрати';

  @override
  String get transportModeCar => 'Автомобил';

  @override
  String get transportModeWalk => 'Пеша';

  @override
  String etaArrivalLabel(String time) {
    return 'Пр. $time';
  }

  @override
  String get supportRoadstr => 'Подкрепи Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address копирано в клипборда';
  }

  @override
  String get disclaimerTitle => 'Важно известие';

  @override
  String get disclaimerAccept => 'Прочетох и приемам';

  @override
  String get disclaimerBody =>
      'Roadstr is an experimental navigation app based on OpenStreetMap data and the Nostr protocol. By using this app the user fully accepts the following conditions:\n\n🚗  ROAD SAFETY\nThe driver must always keep their eyes on the road. Do not look at the phone while driving. Secure the device in an approved, visible mount without diverting attention from the road.\n\n⚠️  LIMITATION OF LIABILITY\nRoadstr is provided \"as is\", without warranties of accuracy, completeness or fitness for any specific purpose. The developers disclaim any liability for damages arising from the use of the application, including but not limited to: traffic accidents, administrative penalties, and damage to property or persons.\n\n🚫  RESTRICTED TRAFFIC ZONES\nNavigation is based on OpenStreetMap data which may not be up to date regarding restricted zones, bus lanes and local restrictions. The user is responsible for independently verifying the accessibility of the suggested route before travelling it. The developers are not liable for any penalties received.\n\n📍  ACCURACY\nGPS tracking may be inaccurate. Road directions are for guidance only. Always observe road signs and markings, which always take precedence over the app\'s instructions.\n\n🔒  PRIVACY\nNo location data is transmitted to external servers. Route calculation is performed via third-party services (OSRM, GraphHopper, OpenRouteService) to which only the start and destination coordinates are sent.\n\nBy using Roadstr the user assumes full and complete responsibility for the use of the application and any consequences arising from its use.';

  @override
  String get readOnWikipedia => 'Прочети в Wikipedia';

  @override
  String searchOnEngine(String engine) {
    return 'Търси в $engine';
  }

  @override
  String get plannerFromHint => 'От…';

  @override
  String get plannerToHint => 'Дестинация…';

  @override
  String departEta(String dep, String arr) {
    return 'Заминаване $dep  →  Пристигане $arr';
  }

  @override
  String get modeCar => 'Кола';

  @override
  String get modeBike => 'Велосипед';

  @override
  String get modeWalk => 'Пеша';

  @override
  String windSpeed(String speed) {
    return 'вятър $speed км/ч';
  }

  @override
  String durationMin(int m) {
    return '$m мин';
  }

  @override
  String durationHourMin(int h, int m) {
    return '$hч $mмин';
  }

  @override
  String get weatherClear => 'Ясно';

  @override
  String get weatherPartlyCloudy => 'Променливо';

  @override
  String get weatherCloudy => 'Облачно';

  @override
  String get weatherFog => 'Мъгла';

  @override
  String get weatherLightRain => 'Ситен дъжд';

  @override
  String get weatherRain => 'Дъжд';

  @override
  String get weatherSnow => 'Сняг';

  @override
  String get weatherShowers => 'Валежи';

  @override
  String get weatherThunderstorm => 'Гръмотевична буря';

  @override
  String get ztlAheadWarning =>
      'ZTL зона напред — маршрутът влиза в ограничена зона';

  @override
  String get ztlInsideWarning => 'Зона с ограничено движение';

  @override
  String get onboardingAppSubtitle => 'Open-source Nostr навигация';

  @override
  String get onboardingWelcomeTitle => 'Добре дошли';

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
  String get onboardingGetStarted => 'Започнете';

  @override
  String get onboardingNostrTitle => 'Nostr самоличност';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => 'Свързан';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (препоръчително)';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec ключ';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Невалиден nsec ключ — проверете и опитайте отново.';

  @override
  String get onboardingSkip => 'Пропуснете засега';

  @override
  String get onboardingContinue => 'Продължи';

  @override
  String get onboardingEnterNsec => 'Въведете nsec ключ';

  @override
  String get onboardingSetupTitle => 'Настройте Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => 'Местоположение';

  @override
  String get onboardingLocationGranted =>
      'Достъпът до местоположението е разрешен';

  @override
  String get onboardingLocationRequired => 'Необходимо за GPS навигация';

  @override
  String get onboardingGrantButton => 'Разреши';

  @override
  String get onboardingGrapheneTitle => 'Потребители на GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'Гласово водене с ИИ (незадължително)';

  @override
  String get onboardingVoiceReady => 'Гласовият модел Kokoro е готов';

  @override
  String get onboardingVoiceDownloading => 'Изтегляне на гласов модел…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'Изтегли';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'Готови сте да навигирате!';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'Да тръгваме!';

  @override
  String get onboardingProfileLoading => 'Зареждане на профил…';

  @override
  String get onboardingNsecHint => 'nsec1…';
}
