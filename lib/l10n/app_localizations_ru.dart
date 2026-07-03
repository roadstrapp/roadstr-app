// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppLocalizationsRu extends AppLocalizations {
  AppLocalizationsRu([String locale = 'ru']) : super(locale);

  @override
  String get searchHint => 'Куда вы хотите поехать?';

  @override
  String get calculatingRoute => 'Вычисление маршрута…';

  @override
  String get routeNotFoundTitle => 'Маршрут не найден';

  @override
  String get noRouteFound => 'Маршрут не найден. Проверьте соединение.';

  @override
  String get emptyServerResponse =>
      'Пустой ответ сервера. Проверьте соединение.';

  @override
  String routeError(String error) {
    return 'Ошибка расчёта маршрута: $error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS недоступен — Settings → App → Roadstr → Permissions → Location';

  @override
  String get acquiringGps => 'Получение GPS…';

  @override
  String get graphhopperServerNotConfigured =>
      'Сервер GraphHopper не настроен — используется OSRM';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'API-ключ GraphHopper не настроен — используется OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'API-ключ OpenRouteService не настроен — используется OSRM';

  @override
  String get chooseRoute => 'Выбрать маршрут';

  @override
  String routeOptionsCount(int count) {
    return '$count вариантов';
  }

  @override
  String get cancel => 'Отмена';

  @override
  String get startNavigation => 'Начать навигацию';

  @override
  String get fastestRoute => 'Быстрейший';

  @override
  String get now => 'Сейчас';

  @override
  String get history => 'История';

  @override
  String get clearHistory => 'Очистить';

  @override
  String get selectedPosition => 'Выбранная позиция';

  @override
  String get bottomBarProfile => 'Профиль';

  @override
  String get bottomBarMenu => 'Меню';

  @override
  String get settingsTitle => 'Настройки';

  @override
  String get sectionTheme => 'Тема';

  @override
  String get sectionMap => 'Карта';

  @override
  String get sectionInfo => 'Информация';

  @override
  String get sectionLanguage => 'Язык';

  @override
  String get themeLightNostr => 'Светлая · Nostr Фиолетовый';

  @override
  String get themeLightBitcoin => 'Светлая · Bitcoin Оранжевый';

  @override
  String get themeDarkNostr => 'Тёмная · Nostr Фиолетовый';

  @override
  String get themeDarkBitcoin => 'Тёмная · Bitcoin Оранжевый';

  @override
  String get langSystem => 'Системный язык';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => 'Не выключать экран';

  @override
  String get keepScreenOnDescription => 'Предотвращает сон во время навигации';

  @override
  String get rotateMap => 'Карта следует направлению';

  @override
  String get rotateMapDescription => 'Карта вращается по направлению движения';

  @override
  String get mapTileUrlLabel => 'URL тайлов карты';

  @override
  String get routingProviderLabel => 'Поставщик маршрутизации';

  @override
  String get osrmProvider => 'OSRM (публичный, ключ не нужен)';

  @override
  String get graphhopperLocalProvider => 'GraphHopper (локальный/частный)';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud (API-ключ)';

  @override
  String get openrouteProvider => 'OpenRouteService (API-ключ)';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'API-ключ GraphHopper (необязательно)';

  @override
  String get verify => 'Проверить';

  @override
  String get graphhopperServerUrlRequired =>
      'Введите URL сервера перед проверкой.';

  @override
  String get successTitle => 'Успешно';

  @override
  String get graphhopperServerReachable => 'Сервер GraphHopper доступен';

  @override
  String get errorTitle => 'Ошибка';

  @override
  String get close => 'Закрыть';

  @override
  String get infoVersion => 'Версия';

  @override
  String get infoProtocol => 'Протокол';

  @override
  String get infoMaps => 'Карты';

  @override
  String get infoRouting => 'Маршрутизация';

  @override
  String get infoSource => 'Источник';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper (на своём сервере)';

  @override
  String get providerGraphhopperCloud => 'GraphHopper (облако)';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'Профиль';

  @override
  String get notConnected => 'Не подключён';

  @override
  String get loginWithNostrTitle => 'ВОЙТИ ЧЕРЕЗ NOSTR';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription =>
      'Приватный ключ никогда не покидает ваше устройство · Рекомендуется';

  @override
  String get nsecLoginOption => 'Ввести nsec';

  @override
  String get nsecLoginDescription =>
      'Приватный ключ хранится локально · Менее безопасно';

  @override
  String get connectedViaAmber => 'Подключено через Amber (NIP-55)';

  @override
  String get connectedViaNsec => 'Подключено через nsec';

  @override
  String get publicKeyLabel => 'ПУБЛИЧНЫЙ КЛЮЧ';

  @override
  String get npubCopiedToClipboard => 'npub скопирован в буфер обмена';

  @override
  String get logoutTitle => 'Отключиться';

  @override
  String get logoutConfirmation =>
      'Удалить учётные данные Nostr с этого устройства?';

  @override
  String get logoutButton => 'Отключиться';

  @override
  String get nostrIdentityInfo =>
      'С идентификатором Nostr вы можете публиковать дорожные предупреждения, сообщения и точки интереса децентрализованным способом в сети Nostr без центральных серверов.';

  @override
  String get warningTitle => 'Предупреждение';

  @override
  String get nsecWarning =>
      'Вы собираетесь напрямую вбить ваш приватный ключ Nostr прямо в приложение. Любой, кто имеет физический или удалённый доступ к вашему устройству, сможет прочитать его и навсегда захватить контроль над вашей идентификацией Nostr.';

  @override
  String get amberSecureMethodHint =>
      '✦  Безопасный метод — Amber (NIP-55): nsec никогда не покидает хранилище подписчика.';

  @override
  String get nsecRiskAcknowledgment => 'Я понимаю риск и хочу продолжить';

  @override
  String get continueButton => 'Продолжить';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber — это совместимый с NIP-55 подписчик приложений для Android. Ваш приватный ключ остаётся изолированным внутри Amber и никогда не передаётся.';

  @override
  String get requestKeyFromAmber => 'Запросить публичный ключ из Amber';

  @override
  String get amberNotFound =>
      'Amber не найден. Установите его из Play Store или введите npub вручную.';

  @override
  String get waitingForAmberResponse => 'Ожидание ответа от Amber…';

  @override
  String get pasteNpubManually => 'Вставьте npub вручную:';

  @override
  String get confirmNpub => 'Подтвердить npub';

  @override
  String get enterNsecTitle => 'Ввести nsec';

  @override
  String get loginButton => 'Войти';

  @override
  String get invalidNpubTitle => 'Неверный npub';

  @override
  String get invalidNsecTitle => 'Неверный nsec';

  @override
  String get invalidNpubMessage =>
      'Убедитесь, что вы вставили правильный npub.';

  @override
  String get invalidNsecMessage =>
      'Убедитесь, что вы вставили правильный nsec.';

  @override
  String get amberResponseError => 'Ошибка ответа Amber';

  @override
  String get ok => 'ОК';

  @override
  String get or => 'или';

  @override
  String get gpsNotActiveTitle => 'GPS не активен';

  @override
  String get gpsDisabledMessage =>
      'Активируйте GPS в настройках устройства, чтобы определить своё местоположение и использовать навигацию.';

  @override
  String get openSettings => 'Настройки';

  @override
  String get myLocation => 'Моё местоположение';

  @override
  String get loginToReport =>
      'Войдите через Nostr (раздел Профиль), чтобы сообщать о событиях';

  @override
  String get navigateHere => 'Маршрут сюда';

  @override
  String get reportEventHere => 'Сообщить о событии здесь';

  @override
  String get zapSendSats => 'Zap ⚡ (отправить sats)';

  @override
  String get sendZap => 'Отправить Zap';

  @override
  String get chooseAmountSats => 'Выберите сумму в сатоши (sats):';

  @override
  String get customAmount => 'Произвольная сумма…';

  @override
  String get zapSending => 'Отправка…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Получение адреса Lightning…';

  @override
  String get noLightningAddress => 'У этого репортёра нет адреса Lightning';

  @override
  String get requestingInvoice => 'Запрос счёта…';

  @override
  String get lnurlUnavailable => 'LNURL недоступен';

  @override
  String get invoiceFailed => 'Не удалось сгенерировать счёт';

  @override
  String get openingWallet => 'Открытие кошелька…';

  @override
  String get payingViaNwc => 'Оплата через NWC…';

  @override
  String get noLightningWallet => 'Lightning-кошелёк не найден';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats отправлено!';
  }

  @override
  String get stillThere => 'Всё ещё там';

  @override
  String get notThereAnymore => 'Больше нет';

  @override
  String get loginToConfirm =>
      'Войдите через Nostr, чтобы подтвердить или опровергнуть';

  @override
  String get reportAnEvent => 'Сообщить о событии';

  @override
  String get optionalComment => 'Необязательный комментарий…';

  @override
  String get publish => 'Опубликовать';

  @override
  String get publishing => 'Публикация…';

  @override
  String get reportPublished => 'Отчёт опубликован ✓';

  @override
  String get myReports => 'МОИ ОТЧЁТЫ';

  @override
  String get noReportsYet => 'Нет опубликованных отчётов';

  @override
  String get zapBalance => 'Zap-баланс';

  @override
  String get satoshiFromReports => 'Сатоши, полученные за ваши отчёты';

  @override
  String get reputationHigh => 'Высокая';

  @override
  String get reputationMedium => 'Средняя';

  @override
  String get reputationLow => 'Низкая';

  @override
  String reputationLabel(String level) {
    return 'Репутация $level';
  }

  @override
  String reliability(int pct) {
    return 'Надёжность: $pct%';
  }

  @override
  String get confirmedLabel => 'Подтверждено';

  @override
  String get removedLabel => 'Удалено';

  @override
  String get positionLabel => 'Позиция';

  @override
  String get loadingLabel => 'Загрузка…';

  @override
  String get sectionWebSearch => 'Веб-поиск';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect (NWC)';

  @override
  String get nwcDesc =>
      'Вставьте ваш NWC URI (Alby Hub, Mutiny, Cashu…), чтобы оплачивать Zaps прямо из приложения.';

  @override
  String get searchEngineQwantDesc =>
      'Европейский, приватность прежде всего. Без отслеживания, без рекламных профилей. Рекомендуется.';

  @override
  String get searchEngineBraveDesc =>
      'Независимый индекс, открытый исходный код. Без зависимости от Google или Bing. Никакого профилирования.';

  @override
  String get searchEngineDdgDesc =>
      'Ориентирован на конфиденциальность и популярен. Результаты частично от Bing — имейте это в виду.';

  @override
  String get searchEngineStartpageDesc =>
      'Результаты Google без передачи ваших данных Google. Разумный компромисс.';

  @override
  String get searchEngineGoogleDesc =>
      'Очень эффективно. Но помните: Google знает вас лучше вашей мамы и продаёт всё рекламодателям. Ваш выбор. 🍪';

  @override
  String get categoryPolice => 'Полиция';

  @override
  String get categorySpeedCamera => 'Камера контроля скорости';

  @override
  String get categoryTrafficJam => 'Пробка';

  @override
  String get categoryAccident => 'ДТП';

  @override
  String get categoryRoadClosure => 'Перекрытие дороги';

  @override
  String get categoryConstruction => 'Дорожные работы';

  @override
  String get categoryHazard => 'Опасность';

  @override
  String get categoryRoadCondition => 'Состояние дороги';

  @override
  String get categoryPothole => 'Яма';

  @override
  String get categoryFog => 'Туман';

  @override
  String get categoryIce => 'Лёд';

  @override
  String get categoryAnimal => 'Животное';

  @override
  String get categoryOther => 'Другое';

  @override
  String get dateTimeLabel => 'Дата / время';

  @override
  String minutesAgo(int count) {
    return '$count мин назад';
  }

  @override
  String hoursAgo(int count) {
    return '$countч назад';
  }

  @override
  String daysAgo(int count) {
    return '$countд назад';
  }

  @override
  String get sectionFavorites => 'Сохранённые места';

  @override
  String get addFavorite => 'Добавить место';

  @override
  String get favoriteLabelHint => 'Название (напр. Дом, Работа)';

  @override
  String get favoriteAddressHint => 'Адрес';

  @override
  String get favoriteGeocodingError =>
      'Адрес не найден. Попробуйте указать точнее.';

  @override
  String get trafficAlertTitle => 'Новый трафик на маршруте';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category сообщено $age на вашем маршруте.\n\nХотите найти альтернативный маршрут?';
  }

  @override
  String get trafficContinue => 'Продолжить';

  @override
  String get trafficRecalculate => 'Пересчитать маршрут';

  @override
  String get navExitTitle => 'Выйти из навигации?';

  @override
  String get navExitBody => 'Хотите остановить навигацию и вернуться на карту?';

  @override
  String get navContinue => 'Продолжить навигацию';

  @override
  String get navExit => 'Да, выйти';

  @override
  String get loadingInfo => 'Загрузка информации…';

  @override
  String get conditionsOnRoute => 'Условия на маршруте';

  @override
  String get calculateRoute => 'Рассчитать маршрут';

  @override
  String get sectionNavigationVoice => 'Голос навигации';

  @override
  String get voiceGuidance => 'Голосовое сопровождение';

  @override
  String get voiceGuidanceDesc =>
      'Озвучивать инструкции поворотов во время навигации';

  @override
  String get testVoiceEngine => 'Проверить голосовой движок';

  @override
  String get testVoiceEngineDesc =>
      'Нажмите, чтобы проверить TTS-движок и получить инструкции по настройке';

  @override
  String get ttsDialogTitle => 'Голосовой движок отсутствует';

  @override
  String get ttsDialogBody =>
      'Не найден работающий движок Text-to-Speech.\n\nRoadstr использует только программное обеспечение с открытым исходным кодом — установите один из этих бесплатных движков из F-Droid:';

  @override
  String get ttsRhvoiceDesc =>
      'Естественное звучание голоса, ограниченный список языков';

  @override
  String get ttsEspeakDesc =>
      'Поддерживает более 100 языков, роботизированный голос';

  @override
  String get ttsInstallNote =>
      '⚠️ После установки:\n1. Настройки Android → Специальные возможности → Синтез речи\n2. Выберите только что установленный движок\n3. Загрузите голосовые данные для вашего языка\n4. Полностью перезапустите Roadstr';

  @override
  String get ttsTestNow => 'Проверить сейчас';

  @override
  String get voiceUnsupportedTitle => 'Голосовое сопровождение недоступно';

  @override
  String get voiceUnsupportedBody =>
      'Ваш язык пока не поддерживается для голосовых инструкций. Инструкции навигации по-прежнему будут отображаться в виде текста на экране.';

  @override
  String get kokoroModelTitle => 'Голосовая модель (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => 'Не загружено · ~82 МБ';

  @override
  String get kokoroModelStatusDownloading => 'Загрузка...';

  @override
  String get kokoroModelStatusReady => 'Голосовая модель готова';

  @override
  String get kokoroModelDownloadBtn => 'Загрузить';

  @override
  String get kokoroModelSupportedLangs =>
      'Поддерживает: итальянский, английский, испанский, французский, японский, китайский, португальский';

  @override
  String get autoDarkMode => 'Автоматическая тёмная тема';

  @override
  String get autoDarkModeDesc => 'Активирует тёмную тему на закате';
}
