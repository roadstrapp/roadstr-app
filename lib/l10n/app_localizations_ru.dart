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
  String get bottomBarNotifications => 'Уведомления';

  @override
  String get bottomBarProfile => 'Профиль';

  @override
  String get bottomBarMenu => 'Меню';

  @override
  String get notificationsTitle => 'Уведомления';

  @override
  String get notificationsEmpty => 'Уведомлений пока нет';

  @override
  String get notificationsEmptyBody =>
      'Здесь появятся запы и реакции на ваши дорожные сообщения.';

  @override
  String get notificationsLoginRequired => 'Подключите профиль Nostr';

  @override
  String get notificationsLoginRequiredBody =>
      'Войдите через Amber или nsec, чтобы получать уведомления от других пользователей.';

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
  String get keepScreenOn => 'Не выключать экран';

  @override
  String get keepScreenOnDescription => 'Предотвращает сон во время навигации';

  @override
  String get autoCenterOnLaunch =>
      'Центрировать по моему местоположению при запуске';

  @override
  String get autoCenterOnLaunchDesc =>
      'Автоматически использует GPS, только если разрешение на местоположение уже предоставлено';

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
  String get profileVisibilityTitle => 'Видимость профиля';

  @override
  String get profileVisibilityDesc =>
      'Выберите, видят ли другие пользователи Roadstr ваше имя в Nostr, npub и фото.';

  @override
  String get profileVisibilityClear => 'Показывать профиль публично';

  @override
  String get profileVisibilityPseudonymous =>
      'Псевдонимный режим (рекомендуется)';

  @override
  String get profileVisibilityPublishError =>
      'Выбор сохранён локально, но пока не удалось опубликовать его в Nostr.';

  @override
  String get nostrichLabel => 'nostrich';

  @override
  String get profileHiddenNotice =>
      'Этот пользователь использует псевдонимный режим.';

  @override
  String get editSpeedLimit => 'Добавить или обновить ограничение скорости';

  @override
  String get requestSpeedLimit => 'Предложить ограничение скорости';

  @override
  String get acceptEditRequest => 'Принять предложение';

  @override
  String get pendingEditRequests => 'Ожидающие предложения правок';

  @override
  String get speedLimitHint => 'Ограничение скорости';

  @override
  String get speedLimitSaved => 'Ограничение скорости опубликовано';

  @override
  String get editRequestSent => 'Предложение отправлено автору сообщения';

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
  String get waitingForAmberResponse => 'Ожидание ответа от Amber…';

  @override
  String get enterNsecTitle => 'Ввести nsec';

  @override
  String get loginButton => 'Войти';

  @override
  String get invalidNpubTitle => 'Неверный npub';

  @override
  String get invalidNsecTitle => 'Неверный nsec';

  @override
  String get invalidNsecMessage =>
      'Убедитесь, что вы вставили правильный nsec.';

  @override
  String get ok => 'ОК';

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
  String get notifZapTitle => 'Получен zap';

  @override
  String notifZapBody(int sat) {
    return 'Вы получили zap на $sat сат!';
  }

  @override
  String get notifConfirmedTitle => 'Отчёт подтверждён';

  @override
  String notifConfirmedBody(String category) {
    return 'Ваш отчёт «$category» подтвердил другой водитель';
  }

  @override
  String get notifDeniedTitle => 'Отчёт оспорен';

  @override
  String notifDeniedBody(String category) {
    return 'Кто-то сообщил, что вашего $category там больше нет';
  }

  @override
  String chainedManeuver(String first, String second) {
    return '$first, затем $second';
  }

  @override
  String thenManeuver(String instruction) {
    return 'затем $instruction';
  }

  @override
  String get reportSpeedLimitHint => 'Ограничение скорости (необязательно)';

  @override
  String get reportedSpeedLimit => 'Заявленное ограничение скорости';

  @override
  String speedCameraVoiceAlert(int limit, String unit) {
    return 'Камера, ограничение скорости $limit $unit';
  }

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

  @override
  String get settingsImperialUnits => 'Имперские единицы';

  @override
  String get settingsImperialUnitsDesc =>
      'Мили и футы вместо километров и метров';

  @override
  String get arrivedTitle => '🎉 Вы прибыли!';

  @override
  String get transportModeCar => 'Автомобиль';

  @override
  String get transportModeWalk => 'Пешком';

  @override
  String etaArrivalLabel(String time) {
    return 'Приб. $time';
  }

  @override
  String get supportRoadstr => 'Поддержать Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address скопировано в буфер обмена';
  }

  @override
  String get disclaimerTitle => 'Важное уведомление';

  @override
  String get disclaimerAccept => 'Я прочитал(а) и принимаю';

  @override
  String get disclaimerBody =>
      'Roadstr — это экспериментальное навигационное приложение с открытым исходным кодом, поддерживаемое сообществом, основанное на данных OpenStreetMap и протоколе Nostr, доступное для использования в любой стране. Загружая, устанавливая или используя данное приложение, пользователь безоговорочно принимает все нижеследующие условия без территориальных ограничений.\n\n🚗 БЕЗОПАСНОСТЬ ДОРОЖНОГО ДВИЖЕНИЯ ПРЕВЫШЕ ВСЕГО\nВодитель обязан всегда следить за дорогой и соблюдать все применимые правила дорожного движения и установленные дорожные знаки, которые всегда имеют приоритет над любыми указаниями приложения. Категорически запрещается управлять устройством во время движения; перед началом поездки закрепите его в утверждённом, хорошо видимом креплении и никогда не отвлекайтесь от дороги для взаимодействия с устройством, пока транспортное средство находится в движении.\n\n⚠️ ПРИНЯТИЕ РИСКА — ПО ВСЕМУ МИРУ\nИспользуя Roadstr в любой стране и в рамках любой правовой системы, пользователь сознательно и добровольно принимает на себя ВСЕ риски, связанные с его использованием, включая, помимо прочего: дорожно-транспортные происшествия, телесные повреждения, смерть, материальный ущерб, повреждение транспортного средства, штрафы, административные санкции, эвакуацию, изъятие транспортного средства, уголовную ответственность или любые иные последствия, прямо или косвенно связанные с использованием приложения. Пользователь единолично несёт полную ответственность за каждое решение, принятое при вождении и навигации.\n\n🚫 ОТСУТСТВИЕ ГАРАНТИЙ\nRoadstr предоставляется строго «КАК ЕСТЬ» и «КАК ДОСТУПНО», без каких-либо гарантий, явных, подразумеваемых или предусмотренных законом, включая, помимо прочего, гарантии точности, полноты, надёжности, доступности, коммерческой пригодности, пригодности для определённой цели и отсутствия нарушения прав третьих лиц. Картографические данные, расчёт маршрутов, ограничения скорости, камеры контроля скорости и информация о зонах ограниченного движения (ZTL/ZAC/LTZ) получены из открытых источников, поддерживаемых сообществом (OpenStreetMap, Overpass API), которые могут быть неполными, устаревшими или неточными для любой страны, региона или муниципалитета в любой момент времени и без предварительного уведомления. Пользователь несёт единоличную ответственность за самостоятельную проверку до и во время поездки законности и проходимости любого предложенного маршрута на основании официальных местных дорожных знаков и правил.\n\n📍 ТОЧНОСТЬ И GPS\nОпределение местоположения по GPS может быть неточным или недоступным. Все указания направления, расстояния и оповещения предоставляются исключительно в информационных целях и никогда не должны служить единственным основанием для принятия решения при вождении.\n\n🛡️ ОГРАНИЧЕНИЕ ОТВЕТСТВЕННОСТИ\nВ максимальной степени, разрешённой применимым законодательством в соответствующей юрисдикции, разработчики, участники проекта и любые стороны, причастные к созданию или распространению Roadstr, не несут ответственности за какие-либо прямые, косвенные, случайные, косвенные (последующие), особые, штрафные или карательные убытки любого рода, включая телесные повреждения, смерть или финансовые потери, возникшие вследствие использования приложения либо невозможности его использования, даже если они были уведомлены о возможности возникновения таких убытков. В юрисдикциях, где данное ограничение полностью или частично не допускается, ответственность ограничивается минимальным объёмом, разрешённым законодательством соответствующей юрисдикции.\n\n🤝 ВОЗМЕЩЕНИЕ УБЫТКОВ\nПользователь обязуется возместить убытки и оградить от ответственности разработчиков и участников проекта в связи с любыми претензиями, убытками, потерями или расходами (включая расходы на юридические услуги), возникшими вследствие использования приложения пользователем или нарушения им настоящих условий.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ ДЕЛИМОСТЬ ПОЛОЖЕНИЙ\nЕсли какое-либо положение настоящих условий будет признано не имеющим юридической силы в определённой юрисдикции, такое положение будет ограничено или исключено в минимально необходимой степени, а все остальные положения сохранят полную силу и действие.\n\nИспользуя Roadstr в любой точке мира, пользователь подтверждает, что прочитал, понял и безоговорочно принял все вышеуказанные условия, а также принимает на себя полную и безусловную ответственность — и весь риск — за использование приложения и любые последствия, возникающие в результате этого.';

  @override
  String get readOnWikipedia => 'Читать в Википедии';

  @override
  String get openInBrowser => 'Открыть в браузере';

  @override
  String get wikipediaLoadFailed => 'Не удалось безопасно загрузить Википедию.';

  @override
  String get retry => 'Повторить';

  @override
  String get poiDetailsFromOsm => 'Информация из OpenStreetMap';

  @override
  String get poiCategory => 'Категория';

  @override
  String get poiOperator => 'Оператор';

  @override
  String get poiCuisine => 'Кухня';

  @override
  String get poiWheelchairYes => 'Доступно для инвалидных колясок';

  @override
  String get poiWheelchairLimited =>
      'Ограниченный доступ для инвалидных колясок';

  @override
  String get poiWheelchairNo => 'Недоступно для инвалидных колясок';

  @override
  String get poiContact => 'Контакты';

  @override
  String get poiAddress => 'Адрес';

  @override
  String get poiWebsite => 'Веб-сайт';

  @override
  String get poiAccessPrivate => 'Частный доступ';

  @override
  String get poiAccessCustomers => 'Только для клиентов';

  @override
  String get poiAccessPermit => 'Требуется разрешение';

  @override
  String get poiAccessNo => 'Нет общего доступа';

  @override
  String get poiAccessDestination => 'Доступ только к месту назначения';

  @override
  String get poiLightningAccepted => 'Принимает Lightning';

  @override
  String get poiBitcoinAccepted => 'Принимает Bitcoin';

  @override
  String get poiParkingDetails => 'Парковка';

  @override
  String get poiParkingSurface => 'Наземная';

  @override
  String get poiParkingUnderground => 'Подземная';

  @override
  String get poiParkingMultiStorey => 'Многоуровневая';

  @override
  String get poiParkingStreetSide => 'У обочины';

  @override
  String get poiParkingLane => 'На улице';

  @override
  String get poiParkingRooftop => 'На крыше';

  @override
  String get poiFee => 'Плата';

  @override
  String get poiFree => 'Бесплатно';

  @override
  String get poiPaid => 'Платно';

  @override
  String get poiCapacity => 'Вместимость';

  @override
  String get poiMaxStay => 'Максимальное время стоянки';

  @override
  String get poiPrice => 'Цена';

  @override
  String get poiChargingDetails => 'Зарядка';

  @override
  String get poiConnectorType2 => 'Тип 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Дизель';

  @override
  String get poiPetrol95 => 'Бензин 95';

  @override
  String get poiSmokingAllowed => 'Курение разрешено';

  @override
  String get poiSmokingOutside => 'Курение на улице';

  @override
  String get poiSmokingAreas => 'Места для курения';

  @override
  String get poiSmokeFree => 'Без курения';

  @override
  String get poiOutdoorSeating => 'Места на открытом воздухе';

  @override
  String get poiTakeaway => 'На вынос';

  @override
  String get poiTakeawayOnly => 'Только на вынос';

  @override
  String get gpsSignalLost => 'Сигнал GPS потерян';

  @override
  String get poiOpenNow => 'Открыто';

  @override
  String get poiClosedNow => 'Закрыто';

  @override
  String poiOpensAt(String when) {
    return 'Открытие: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Закрытие: $when';
  }

  @override
  String searchOnEngine(String engine) {
    return 'Искать в $engine';
  }

  @override
  String get plannerFromHint => 'Откуда…';

  @override
  String get plannerToHint => 'Пункт назначения…';

  @override
  String departEta(String dep, String arr) {
    return 'Отправление $dep  →  Прибытие $arr';
  }

  @override
  String get modeCar => 'Авто';

  @override
  String get modeBike => 'Велосипед';

  @override
  String get modeWalk => 'Пешком';

  @override
  String windSpeed(String speed) {
    return 'ветер $speed';
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
  String get weatherPartlyCloudy => 'Перем. облачность';

  @override
  String get weatherCloudy => 'Облачно';

  @override
  String get weatherFog => 'Туман';

  @override
  String get weatherLightRain => 'Морось';

  @override
  String get weatherRain => 'Дождь';

  @override
  String get weatherSnow => 'Снег';

  @override
  String get weatherShowers => 'Ливень';

  @override
  String get weatherThunderstorm => 'Гроза';

  @override
  String get ztlInsideWarning => 'Зона ограниченного движения';

  @override
  String get onboardingAppSubtitle =>
      'Навигация Nostr с открытым исходным кодом';

  @override
  String get onboardingWelcomeTitle => 'Добро пожаловать';

  @override
  String get onboardingWelcomeBody =>
      'Всё, что нужно навигационному приложению — без ущерба для вашей конфиденциальности.';

  @override
  String get onboardingFeatureNav => 'Пошаговая GPS-навигация';

  @override
  String get onboardingFeatureNostr =>
      'Дорожные события Nostr (камеры, опасности, пробки)';

  @override
  String get onboardingFeatureLightning =>
      'Чаевые Lightning Network для репортёров событий';

  @override
  String get onboardingFeatureVoice =>
      'Голосовое ведение ИИ на устройстве (Kokoro-82M)';

  @override
  String get onboardingFeaturePrivacy =>
      'Учётная запись не нужна — никакой слежки';

  @override
  String get onboardingGetStarted => 'Начать';

  @override
  String get onboardingNostrTitle => 'Идентификация Nostr';

  @override
  String get onboardingNostrSubtitle =>
      'Необязательно — подключитесь, чтобы сообщать о дорожных событиях, подтверждать предупреждения и получать чаевые Lightning.';

  @override
  String get onboardingNostrConnected => 'Подключено';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55 (рекомендуется)';

  @override
  String get onboardingAmberSubtitle =>
      'Подключитесь через приложение-подписант Amber. Ваш закрытый ключ никогда не покидает Amber.';

  @override
  String get onboardingNsecTitle => 'Ключ nsec';

  @override
  String get onboardingNsecSubtitle =>
      'Вставьте ваш закрытый ключ. Хранится в Android Keystore.';

  @override
  String get onboardingNsecError =>
      'Недействительный ключ nsec — проверьте и попробуйте снова.';

  @override
  String get onboardingSkip => 'Пропустить пока';

  @override
  String get onboardingContinue => 'Продолжить';

  @override
  String get onboardingEnterNsec => 'Введите ключ nsec';

  @override
  String get onboardingSetupTitle => 'Настройте Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Настройте доступ к местоположению и опциональное голосовое ведение.';

  @override
  String get onboardingLocationTitle => 'Местоположение';

  @override
  String get onboardingLocationGranted => 'Доступ к местоположению разрешён';

  @override
  String get onboardingLocationRequired => 'Требуется для GPS-навигации';

  @override
  String get onboardingGrantButton => 'Разрешить';

  @override
  String get onboardingGrapheneTitle => 'Пользователи GrapheneOS';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

  @override
  String get onboardingVoiceTitle => 'Голосовое ведение ИИ (опционально)';

  @override
  String get onboardingVoiceReady => 'Голосовая модель Kokoro готова';

  @override
  String get onboardingVoiceDownloading => 'Загрузка голосовой модели…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Загрузите 82 МБ AI-модель Kokoro для голоса на устройстве';

  @override
  String get onboardingVoiceChecking => 'Проверка статуса модели…';

  @override
  String get onboardingDownloadButton => 'Загрузить';

  @override
  String get onboardingVoiceLaterHint =>
      'Вы также можете загрузить голосовую модель позже в\nНастройки → Голос навигации.';

  @override
  String get onboardingReadyTitle => 'Вы готовы к навигации!';

  @override
  String get onboardingReadyBody =>
      'Roadstr откроет карту.\nОстальное можно настроить в Настройках.';

  @override
  String get onboardingLetsGo => 'Поехали!';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => 'Голос';

  @override
  String get kokoroVoiceFemale => 'Женский';

  @override
  String get kokoroVoiceMale => 'Мужской';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Мужской голос недоступен для этого языка';

  @override
  String get kokoroSpeedTitle => 'Скорость речи';

  @override
  String get kokoroVolumeTitle => 'Громкость голоса';

  @override
  String get onboardingFavoritesSyncNotice =>
      'Необязательно: сохранённое избранное может синхронизироваться между вашими устройствами через реле Nostr со сквозным шифрованием (NIP-44) вашим собственным ключом — реле видят только шифртекст, и никто, кроме вас, не может прочитать содержимое. Включите в любой момент в Настройках.';

  @override
  String get profileVisibilityOnboarding =>
      'Roadstr всегда публикует открытый ключ Nostr, необходимый для проверки сообщения. В псевдонимном режиме другие пользователи Roadstr видят только обычный аватар и «nostrich»; в публичном режиме они видят также ваше имя в Nostr, npub и фото профиля. Ваши сообщения, местоположения, репутация и полученные запы остаются публичными в Nostr.';

  @override
  String get parkingSaveHere => 'Сохранить парковку здесь';

  @override
  String get parkingMarkerTitle => 'Место парковки';

  @override
  String get parkingNavigateHere => 'Навигация к парковке';

  @override
  String get parkingRemove => 'Удалить парковку';

  @override
  String get parkingSavedSnack => 'Место парковки сохранено';

  @override
  String get parkingRemovedSnack => 'Место парковки удалено';

  @override
  String get exportFavoritesTitle => 'Экспорт избранного';

  @override
  String get exportFavoritesDesc =>
      'Сохраните избранные места в файл JSON, который можно зарезервировать или перенести на другое устройство.';

  @override
  String get exportEncryptToggle => 'Зашифровать паролем';

  @override
  String get exportPasswordHint => 'Пароль';

  @override
  String get exportButton => 'Экспорт';

  @override
  String get exportSuccessSnack => 'Избранное экспортировано';

  @override
  String get exportFailedSnack => 'Экспорт не удался';

  @override
  String get importFavoritesTitle => 'Импорт избранного';

  @override
  String get importPasswordPrompt => 'Этот файл зашифрован — введите пароль';

  @override
  String importSuccessSnack(int n) {
    return 'Импортировано избранных: $n';
  }

  @override
  String get importFailedSnack => 'Импорт не удался — проверьте файл и пароль';

  @override
  String get syncFavoritesTitle => 'Синхронизация избранного';

  @override
  String get syncFavoritesDesc =>
      'Публикуйте своё избранное со сквозным шифрованием на ваших реле Nostr, чтобы оно следовало за вами на всех устройствах. Реле видят только шифртекст — никто, кроме вас, не может прочитать содержимое.';

  @override
  String get syncNowButton => 'Отправить в Nostr';

  @override
  String get syncPullButton => 'Получить из Nostr';

  @override
  String get syncPassphraseTitle =>
      'Парольная фраза синхронизации (необязательно)';

  @override
  String get syncPassphraseDesc =>
      'Второй уровень шифрования синхронизируемого избранного: даже если ваш ключ Nostr будет скомпрометирован, снимок на релеях останется нечитаемым без этой фразы. Вы вводите её один раз на каждом новом устройстве. Оставьте пустым, чтобы отключить.';

  @override
  String get syncSuccessSnack => 'Избранное синхронизировано';

  @override
  String get syncFailedSnack => 'Синхронизация не удалась';

  @override
  String syncLastSyncLabel(String when) {
    return 'Последняя синхронизация: $when';
  }

  @override
  String get syncNoIdentity =>
      'Войдите через Nostr (Настройки → Профиль), чтобы включить синхронизацию';

  @override
  String get onboardingVpnNotice =>
      'Для максимальной приватности — сообщения распространяются по сети Nostr — рекомендуется использовать VPN. Mullvad с оплатой в биткоинах — рекомендуемый выбор.';

  @override
  String get trafficNormal => 'Обычный трафик';

  @override
  String get trafficModerate => 'Умеренный трафик';

  @override
  String get trafficHeavy => 'Плотный трафик';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get avoidHighwaysAndTolls =>
      'Избегать автомагистралей и платных дорог';

  @override
  String get avoidRouteUnavailable =>
      'Маршрут без автомагистралей и платных дорог не найден.';

  @override
  String get avoidanceUnavoidableSection =>
      'Минимизирует автомагистрали/платные дороги · неизбежный участок';

  @override
  String get nearbyTitle => 'Рядом';

  @override
  String get nearbyNeedsGps => 'Ожидание GPS';

  @override
  String get nearbyNothingFound => 'В радиусе 5 км ничего не найдено';

  @override
  String get nearbyFuel => 'АЗС';

  @override
  String get nearbySupermarket => 'Супермаркет';

  @override
  String get nearbyAtm => 'Банкомат';

  @override
  String get nearbyPharmacy => 'Аптека';

  @override
  String get nearbyHospital => 'Больница';

  @override
  String get nearbyPolice => 'Полиция';

  @override
  String get nearbyPostOffice => 'Почта';

  @override
  String get nearbyParking => 'Парковка';

  @override
  String get nearbyCharging => 'Зарядка';
}
