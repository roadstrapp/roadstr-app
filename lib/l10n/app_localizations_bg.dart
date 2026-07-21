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
  String get bottomBarNotifications => 'Notifications';

  @override
  String get bottomBarProfile => 'Профил';

  @override
  String get bottomBarMenu => 'Меню';

  @override
  String get notificationsTitle => 'Notifications';

  @override
  String get notificationsEmpty => 'No notifications yet';

  @override
  String get notificationsEmptyBody =>
      'Zaps and reactions to your road reports will appear here.';

  @override
  String get notificationsLoginRequired => 'Connect your Nostr profile';

  @override
  String get notificationsLoginRequiredBody =>
      'Sign in with Amber or nsec to receive notifications from other users.';

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
  String get autoCenterOnLaunch =>
      'Центриране върху моята позиция при стартиране';

  @override
  String get autoCenterOnLaunchDesc =>
      'Използва GPS автоматично само ако разрешението за местоположение вече е дадено';

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
  String get notifZapTitle => 'Получен zap';

  @override
  String notifZapBody(int sat) {
    return 'Получихте zap от $sat сата!';
  }

  @override
  String get notifConfirmedTitle => 'Сигналът е потвърден';

  @override
  String notifConfirmedBody(String category) {
    return 'Вашият сигнал за $category беше потвърден от друг шофьор';
  }

  @override
  String get notifDeniedTitle => 'Сигналът е оспорен';

  @override
  String notifDeniedBody(String category) {
    return 'Някой съобщи, че вашето $category вече го няма';
  }

  @override
  String chainedManeuver(String first, String second) {
    return '$first, след това $second';
  }

  @override
  String get reportSpeedLimitHint => 'Ограничение на скоростта (по избор)';

  @override
  String get reportedSpeedLimit => 'Съобщено ограничение на скоростта';

  @override
  String speedCameraVoiceAlert(int limit, String unit) {
    return 'Камера, ограничение $limit $unit';
  }

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
  String get settingsImperialUnits => 'Имперски единици';

  @override
  String get settingsImperialUnitsDesc =>
      'Мили и фийта вместо километри и метри';

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
      'Roadstr е експериментално, приложение с отворен код за навигация, поддържано от общността, базирано на данни от OpenStreetMap и протокола Nostr, предоставено за използване във всяка държава. Чрез изтеглянето, инсталирането или използването на това приложение потребителят безусловно приема всички следващи условия, без ограничение на територията.\n\n🚗 БЕЗОПАСНОСТТА НА ПЪТЯ НА ПЪРВО МЯСТО\nВодачът трябва винаги да държи очите си на пътя и да спазва всички приложими закони за движение по пътищата и поставената сигнализация, които винаги имат предимство пред всяка инструкция от приложението. Никога не работете с устройството по време на шофиране; закрепете го на одобрена, видима стойка преди да потеглите, и никога не отклонявайте вниманието си от пътя, за да взаимодействате с него, докато превозното средство се движи.\n\n⚠️ ПОЕМАНЕ НА РИСКА — В ЦЕЛИЯ СВЯТ\nЧрез използването на Roadstr, във всяка държава и при всяка правна система, потребителят съзнателно и доброволно поема ВСИЧКИ рискове, свързани с неговото използване, включително, но не само: пътнотранспортни произшествия, телесни повреди, смърт, имуществени щети, повреди на превозното средство, глоби, административни наказания, репатриране, задържане на автомобил, наказателна отговорност или всякакви други последици, произтичащи пряко или косвено от разчитането на приложението. Единствено потребителят носи пълна отговорност за всяко решение, свързано с шофирането и навигацията.\n\n🚫 БЕЗ ГАРАНЦИЯ\nRoadstr се предоставя стриктно „КАКТО Е“ и „КАКТО Е НАЛИЧНО“, без каквато и да е гаранция от какъвто и да е вид, изрична, подразбираща се или установена по закон — включително, без ограничение, гаранции за точност, пълнота, надеждност, наличност, търговска годност, годност за определена цел и ненарушаване на права. Данните за картата, маршрутизирането, ограниченията на скоростта, камерите за скорост и информацията за зони с ограничено движение (ZTL/ZAC/LTZ) произхождат от отворени, поддържани от общността източници (OpenStreetMap, Overpass API), които могат да бъдат непълни, остарели или неточни за всяка държава, регион или община, по всяко време и без предизвестие. Потребителят носи пълна отговорност самостоятелно да провери, преди и по време на пътуването, законността и достъпността на всеки предложен маршрут спрямо официалната местна сигнализация и разпоредби.\n\n📍 ТОЧНОСТ И GPS\nGPS позиционирането може да бъде неточно или недостъпно. Всички указания, разстояния и предупреждения се предоставят единствено с ориентировъчна цел и никога не трябва да се приемат като единствена основа за решение при шофиране.\n\n🛡️ ОГРАНИЧАВАНЕ НА ОТГОВОРНОСТТА\nДо максималната степен, разрешена от приложимото право във всяка юрисдикция, разработчиците, сътрудниците и всяка страна, участваща в създаването или разпространението на Roadstr, не носят отговорност за каквито и да е преки, косвени, случайни, последващи, специални, примерни или наказателни вреди от какъвто и да е вид — включително телесна повреда, смърт или финансова загуба — произтичащи от или свързани с използването или невъзможността за използване на приложението, дори ако са били уведомени за възможността от такива вреди. Когато дадена юрисдикция не позволява част или цялото от това ограничение, отговорността се ограничава до минималната степен, разрешена от закона в тази юрисдикция.\n\n🤝 ОБЕЗЩЕТЕНИЕ\nПотребителят се съгласява да обезщети и да предпази от вреди разработчиците и сътрудниците от всякакви претенции, вреди, загуби или разходи (включително адвокатски хонорари), произтичащи от използването на приложението от потребителя или от нарушаване на настоящите условия.\n\n🔒 PRIVACY AND NETWORK SERVICES\nRoadstr runs no first-party servers or analytics, but it is not an offline app. When relevant features are used, coordinates or coarse areas and the IP address are sent directly to third parties: routing providers (origin, destination and waypoints), OpenStreetMap/Nominatim/Overpass (search, reverse geocoding, POIs, restricted zones, limits and cameras), Open-Meteo (rounded coordinates), map-tile servers and Nostr relays (city-level geohash). A road report publishes its exact position, time, content and public key to Nostr and is pseudonymous, not anonymous. Favorites and parking remain local unless explicitly exported or synced.\n\n⚖️ РАЗДЕЛИМОСТ\nАко някоя разпоредба от настоящите условия бъде счетена за неприложима в дадена юрисдикция, тази разпоредба ще бъде ограничена или отделена до минималната необходима степен, а всички останали разпоредби ще останат в пълна сила и действие.\n\nЧрез използването на Roadstr, където и да е по света, потребителят потвърждава, че е прочел, разбрал и безусловно приел всяко от горните условия, и поема пълна и цялостна отговорност — и целия риск — за използването на приложението и всяка последица, произтичаща от него.';

  @override
  String get readOnWikipedia => 'Прочети в Wikipedia';

  @override
  String get openInBrowser => 'Отваряне в браузър';

  @override
  String get wikipediaLoadFailed =>
      'Wikipedia не можа да бъде заредена сигурно.';

  @override
  String get retry => 'Опитай отново';

  @override
  String get poiDetailsFromOsm => 'Информация от OpenStreetMap';

  @override
  String get poiCategory => 'Категория';

  @override
  String get poiOperator => 'Оператор';

  @override
  String get poiCuisine => 'Кухня';

  @override
  String get poiAccessibility => 'Достъпност';

  @override
  String get poiWheelchairYes => 'Достъпно за инвалидни колички';

  @override
  String get poiWheelchairLimited => 'Ограничен достъп за инвалидни колички';

  @override
  String get poiWheelchairNo => 'Няма достъп за инвалидни колички';

  @override
  String get poiContact => 'Контакт';

  @override
  String get poiAddress => 'Адрес';

  @override
  String get poiWebsite => 'Уебсайт';

  @override
  String get poiAccessPrivate => 'Частен достъп';

  @override
  String get poiAccessCustomers => 'Само за клиенти';

  @override
  String get poiAccessPermit => 'Изисква се разрешително';

  @override
  String get poiAccessNo => 'Няма обществен достъп';

  @override
  String get poiAccessDestination => 'Достъп само до дестинацията';

  @override
  String get poiLightningAccepted => 'Приема Lightning';

  @override
  String get poiBitcoinAccepted => 'Приема Bitcoin';

  @override
  String get poiParkingDetails => 'Паркинг';

  @override
  String get poiParkingSurface => 'Наземен';

  @override
  String get poiParkingUnderground => 'Подземен';

  @override
  String get poiParkingMultiStorey => 'Многоетажен';

  @override
  String get poiParkingStreetSide => 'Край улицата';

  @override
  String get poiParkingLane => 'На улицата';

  @override
  String get poiParkingRooftop => 'На покрива';

  @override
  String get poiFee => 'Такса';

  @override
  String get poiFree => 'Безплатно';

  @override
  String get poiPaid => 'Платено';

  @override
  String get poiCapacity => 'Капацитет';

  @override
  String get poiMaxStay => 'Максимален престой';

  @override
  String get poiPrice => 'Цена';

  @override
  String get poiChargingDetails => 'Зареждане';

  @override
  String get poiConnectorType2 => 'Тип 2';

  @override
  String get poiConnectorChademo => 'CHAdeMO';

  @override
  String get poiConnectorCcs => 'CCS';

  @override
  String get poiDiesel => 'Дизел';

  @override
  String get poiPetrol95 => 'Бензин 95';

  @override
  String get poiSmokingAllowed => 'Пушенето е разрешено';

  @override
  String get poiSmokingOutside => 'Пушене навън';

  @override
  String get poiSmokingAreas => 'Зони за пушачи';

  @override
  String get poiSmokeFree => 'Без тютюнев дим';

  @override
  String get poiOutdoorSeating => 'Места на открито';

  @override
  String get poiTakeaway => 'За вкъщи';

  @override
  String get poiTakeawayOnly => 'Само за вкъщи';

  @override
  String get gpsSignalLost => 'Изгубен GPS сигнал';

  @override
  String get poiOpenNow => 'Отворено';

  @override
  String get poiClosedNow => 'Затворено';

  @override
  String poiOpensAt(String when) {
    return 'Отваря: $when';
  }

  @override
  String poiClosesAt(String when) {
    return 'Затваря: $when';
  }

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
    return 'вятър $speed';
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
      'Напред има зона с ограничено движение — маршрутът минава през нея';

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
      'Grant Precise location (not Approximate) while using Roadstr in:\nSettings → Apps → Roadstr → Permissions → Location\n\nRoadstr uses a foreground navigation service and does not require permanent background-location access.';

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

  @override
  String get kokoroVoiceGenderTitle => 'Глас';

  @override
  String get kokoroVoiceFemale => 'Женски';

  @override
  String get kokoroVoiceMale => 'Мъжки';

  @override
  String get kokoroVoiceGenderUnavailable =>
      'Мъжки глас не е наличен за този език';

  @override
  String get kokoroSpeedTitle => 'Скорост на говора';

  @override
  String get kokoroVolumeTitle => 'Сила на гласа';

  @override
  String get onboardingFavoritesSyncNotice =>
      'По избор: запазените любими могат да се синхронизират между устройствата ви чрез Nostr релетата, шифровани от край до край (NIP-44) с вашия собствен ключ — релетата виждат само шифрован текст и никой освен вас не може да чете съдържанието. Активирайте по всяко време от Настройки.';

  @override
  String get parkingSaveHere => 'Запази паркинга тук';

  @override
  String get parkingMarkerTitle => 'Място за паркиране';

  @override
  String get parkingNavigateHere => 'Навигирай до паркинга';

  @override
  String get parkingRemove => 'Премахни паркинга';

  @override
  String get parkingSavedSnack => 'Мястото за паркиране е запазено';

  @override
  String get parkingRemovedSnack => 'Мястото за паркиране е премахнато';

  @override
  String get exportFavoritesTitle => 'Експортиране на любими';

  @override
  String get exportFavoritesDesc =>
      'Запазете любимите си места в JSON файл, който можете да архивирате или прехвърлите на друго устройство.';

  @override
  String get exportEncryptToggle => 'Шифроване с парола';

  @override
  String get exportPasswordHint => 'Парола';

  @override
  String get exportButton => 'Експортиране';

  @override
  String get exportSuccessSnack => 'Любимите са експортирани';

  @override
  String get exportFailedSnack => 'Експортирането неуспешно';

  @override
  String get importFavoritesTitle => 'Импортиране на любими';

  @override
  String get importPasswordPrompt => 'Този файл е шифрован — въведете паролата';

  @override
  String importSuccessSnack(int n) {
    return '$n любими са импортирани';
  }

  @override
  String get importFailedSnack =>
      'Импортирането неуспешно — проверете файла и паролата';

  @override
  String get syncFavoritesTitle => 'Синхронизиране на любими';

  @override
  String get syncFavoritesDesc =>
      'Публикувайте любимите си, шифровани от край до край, към вашите Nostr релета, за да ви следват на всички устройства. Релетата виждат само шифрован текст — никой освен вас не може да чете съдържанието.';

  @override
  String get syncNowButton => 'Изпрати към Nostr';

  @override
  String get syncPullButton => 'Изтегли от Nostr';

  @override
  String get syncPassphraseTitle => 'Парола за синхронизация (по избор)';

  @override
  String get syncPassphraseDesc =>
      'Втори слой на шифроване за синхронизираните любими места: дори ключът ви за Nostr да бъде компрометиран, снимката на релетата остава нечетима без тази парола. Въвеждате я веднъж на всяко ново устройство. Оставете празно, за да изключите.';

  @override
  String get syncSuccessSnack => 'Любимите са синхронизирани';

  @override
  String get syncFailedSnack => 'Синхронизацията неуспешна';

  @override
  String syncLastSyncLabel(String when) {
    return 'Последна синхронизация: $when';
  }

  @override
  String get syncNoIdentity =>
      'Влезте с Nostr (Настройки → Профил), за да активирате синхронизацията';

  @override
  String get onboardingVpnNotice =>
      'За максимална поверителност — сигналите се разпространяват в мрежата Nostr — се препоръчва използването на VPN. Mullvad, с плащане в биткойн, е препоръчителният избор.';

  @override
  String get trafficNormal => 'Нормален трафик';

  @override
  String get trafficModerate => 'Умерен трафик';

  @override
  String get trafficHeavy => 'Натоварен трафик';

  @override
  String get avoidHighwaysChip => 'Без магистрали';

  @override
  String get avoidTollsChip => 'Без тол такси';

  @override
  String get preferShorterChip => 'Най-кратък маршрут';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'Преглед на маршрута';

  @override
  String get avoidHighwaysAndTolls => 'Избягвай магистрали и платени пътища';

  @override
  String get avoidRouteUnavailable =>
      'Не е намерен маршрут, който избягва магистрали и платени пътища.';

  @override
  String get avoidanceUnavoidableSection =>
      'Намалява магистрали/такси · неизбежен участък';
}
