// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get searchHint => '您想去哪里？';

  @override
  String get calculatingRoute => '正在计算路线…';

  @override
  String get routeNotFoundTitle => '未找到路线';

  @override
  String get noRouteFound => '未找到路线。请检查您的连接。';

  @override
  String get emptyServerResponse => '服务器响应为空。请检查您的连接。';

  @override
  String routeError(String error) {
    return '路线计算错误：$error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS不可用 — Settings → App → Roadstr → Permissions → Location';

  @override
  String get acquiringGps => '正在获取GPS…';

  @override
  String get graphhopperServerNotConfigured => 'GraphHopper服务器未配置 — 使用OSRM';

  @override
  String get graphhopperApiKeyNotConfigured => 'GraphHopper API密钥未配置 — 使用OSRM';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService API密钥未配置 — 使用OSRM';

  @override
  String get chooseRoute => '选择路线';

  @override
  String routeOptionsCount(int count) {
    return '$count个选项';
  }

  @override
  String get cancel => '取消';

  @override
  String get startNavigation => '开始导航';

  @override
  String get fastestRoute => '最快';

  @override
  String get now => '现在';

  @override
  String get history => '历史记录';

  @override
  String get clearHistory => '清除';

  @override
  String get selectedPosition => '所选位置';

  @override
  String get bottomBarProfile => '个人资料';

  @override
  String get bottomBarMenu => '菜单';

  @override
  String get settingsTitle => '设置';

  @override
  String get sectionTheme => '主题';

  @override
  String get sectionMap => '地图';

  @override
  String get sectionInfo => '信息';

  @override
  String get sectionLanguage => '语言';

  @override
  String get themeLightNostr => '浅色 · Nostr紫';

  @override
  String get themeLightBitcoin => '浅色 · 比特币橙';

  @override
  String get themeDarkNostr => '深色 · Nostr紫';

  @override
  String get themeDarkBitcoin => '深色 · 比特币橙';

  @override
  String get langSystem => '系统默认';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => '保持屏幕常亮';

  @override
  String get keepScreenOnDescription => '导航期间防止屏幕休眠';

  @override
  String get rotateMap => '地图跟随方向';

  @override
  String get rotateMapDescription => '地图根据行驶方向旋转';

  @override
  String get mapTileUrlLabel => '地图瓦片URL';

  @override
  String get routingProviderLabel => '路由提供商';

  @override
  String get osrmProvider => 'OSRM（公共，无需密钥）';

  @override
  String get graphhopperLocalProvider => 'GraphHopper（本地/私有）';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud（API密钥）';

  @override
  String get openrouteProvider => 'OpenRouteService（API密钥）';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper API密钥（可选）';

  @override
  String get verify => '验证';

  @override
  String get graphhopperServerUrlRequired => '验证前请输入服务器URL。';

  @override
  String get successTitle => '成功';

  @override
  String get graphhopperServerReachable => 'GraphHopper服务器可访问';

  @override
  String get errorTitle => '错误';

  @override
  String get close => '关闭';

  @override
  String get infoVersion => '版本';

  @override
  String get infoProtocol => '协议';

  @override
  String get infoMaps => '地图';

  @override
  String get infoRouting => '路由';

  @override
  String get infoSource => '来源';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper（自托管）';

  @override
  String get providerGraphhopperCloud => 'GraphHopper（云端）';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => '个人资料';

  @override
  String get notConnected => '未连接';

  @override
  String get loginWithNostrTitle => '使用NOSTR登录';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription => '私钥永不离开您的设备 · 推荐';

  @override
  String get nsecLoginOption => '输入nsec';

  @override
  String get nsecLoginDescription => '私钥存储在本地 · 安全性较低';

  @override
  String get connectedViaAmber => '通过Amber（NIP-55）连接';

  @override
  String get connectedViaNsec => '通过nsec连接';

  @override
  String get publicKeyLabel => '公钥';

  @override
  String get npubCopiedToClipboard => 'npub已复制到剪贴板';

  @override
  String get logoutTitle => '断开连接';

  @override
  String get logoutConfirmation => '从此设备删除Nostr凭据？';

  @override
  String get logoutButton => '断开连接';

  @override
  String get nostrIdentityInfo =>
      '拥有Nostr身份，您可以以去中心化的方式在Nostr网络上发布交通警报、报告和兴趣点，无需中央服务器。';

  @override
  String get warningTitle => '警告';

  @override
  String get nsecWarning =>
      '您即将把您的Nostr私钥直接硬塞进应用程序。任何能够物理或远程访问您设备的人都可以读取它，并永久夺取您Nostr身份的控制权。';

  @override
  String get amberSecureMethodHint =>
      '✦  安全方法是Amber（NIP-55）：nsec永远不会离开应用签名保险库。';

  @override
  String get nsecRiskAcknowledgment => '我了解风险，仍想继续';

  @override
  String get continueButton => '继续';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'Amber是符合NIP-55标准的Android应用签名器。您的私钥隔离在Amber内部，永不共享。';

  @override
  String get requestKeyFromAmber => '从Amber请求公钥';

  @override
  String get amberNotFound => '未找到Amber。从Play Store安装或手动输入您的npub。';

  @override
  String get waitingForAmberResponse => '等待Amber响应…';

  @override
  String get pasteNpubManually => '手动粘贴您的npub：';

  @override
  String get confirmNpub => '确认npub';

  @override
  String get enterNsecTitle => '输入nsec';

  @override
  String get loginButton => '登录';

  @override
  String get invalidNpubTitle => '无效的npub';

  @override
  String get invalidNsecTitle => '无效的nsec';

  @override
  String get invalidNpubMessage => '请确保您粘贴了正确的npub。';

  @override
  String get invalidNsecMessage => '请确保您粘贴了正确的nsec。';

  @override
  String get amberResponseError => 'Amber响应错误';

  @override
  String get ok => '确定';

  @override
  String get or => '或';

  @override
  String get gpsNotActiveTitle => 'GPS未激活';

  @override
  String get gpsDisabledMessage => '在设备设置中激活GPS以获取您的位置并使用导航。';

  @override
  String get openSettings => '设置';

  @override
  String get myLocation => '我的位置';

  @override
  String get loginToReport => '使用Nostr登录（个人资料部分）以报告事件';

  @override
  String get navigateHere => '导航到此处';

  @override
  String get reportEventHere => '在此报告事件';

  @override
  String get zapSendSats => 'Zap ⚡（发送sats）';

  @override
  String get sendZap => '发送Zap';

  @override
  String get chooseAmountSats => '选择聪（sats）的数量：';

  @override
  String get customAmount => '自定义金额…';

  @override
  String get zapSending => '发送中…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => '正在获取闪电地址…';

  @override
  String get noLightningAddress => '此报告者没有闪电地址';

  @override
  String get requestingInvoice => '请求发票…';

  @override
  String get lnurlUnavailable => 'LNURL不可用';

  @override
  String get invoiceFailed => '无法生成发票';

  @override
  String get openingWallet => '正在打开钱包…';

  @override
  String get payingViaNwc => '通过NWC支付…';

  @override
  String get noLightningWallet => '未找到闪电钱包';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats已发送！';
  }

  @override
  String get stillThere => '仍然存在';

  @override
  String get notThereAnymore => '已不存在';

  @override
  String get loginToConfirm => '使用Nostr登录以确认或反驳';

  @override
  String get reportAnEvent => '报告事件';

  @override
  String get optionalComment => '可选评论…';

  @override
  String get publish => '发布';

  @override
  String get publishing => '发布中…';

  @override
  String get reportPublished => '报告已发布 ✓';

  @override
  String get myReports => '我的报告';

  @override
  String get noReportsYet => '尚未发布报告';

  @override
  String get zapBalance => 'Zap余额';

  @override
  String get satoshiFromReports => '从您的报告中收到的聪';

  @override
  String get reputationHigh => '高';

  @override
  String get reputationMedium => '中';

  @override
  String get reputationLow => '低';

  @override
  String reputationLabel(String level) {
    return '声誉 $level';
  }

  @override
  String reliability(int pct) {
    return '可靠性：$pct%';
  }

  @override
  String get confirmedLabel => '已确认';

  @override
  String get removedLabel => '已删除';

  @override
  String get positionLabel => '位置';

  @override
  String get loadingLabel => '加载中…';

  @override
  String get sectionWebSearch => '网络搜索';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect（NWC）';

  @override
  String get nwcDesc => '粘贴您的NWC URI（Alby Hub、Mutiny、Cashu…）以直接从应用支付Zaps。';

  @override
  String get searchEngineQwantDesc => '欧洲引擎，隐私优先。无追踪，无广告档案。推荐。';

  @override
  String get searchEngineBraveDesc => '独立索引，开源。不依赖Google或Bing。零分析。';

  @override
  String get searchEngineDdgDesc => '注重隐私且广受欢迎。部分结果来自Bing——请记住这一点。';

  @override
  String get searchEngineStartpageDesc => 'Google结果，但不把您的数据交给Google。合理的折衷。';

  @override
  String get searchEngineGoogleDesc =>
      '非常有效。但记住：Google比您妈妈更了解您，并把一切都卖给广告商。您自己决定吧。🍪';

  @override
  String get categoryPolice => '警察';

  @override
  String get categorySpeedCamera => '测速摄像头';

  @override
  String get categoryTrafficJam => '交通堵塞';

  @override
  String get categoryAccident => '事故';

  @override
  String get categoryRoadClosure => '道路封闭';

  @override
  String get categoryConstruction => '施工';

  @override
  String get categoryHazard => '危险';

  @override
  String get categoryRoadCondition => '路况';

  @override
  String get categoryPothole => '坑洞';

  @override
  String get categoryFog => '雾';

  @override
  String get categoryIce => '冰';

  @override
  String get categoryAnimal => '动物';

  @override
  String get categoryOther => '其他';

  @override
  String get dateTimeLabel => '日期 / 时间';

  @override
  String minutesAgo(int count) {
    return '$count分钟前';
  }

  @override
  String hoursAgo(int count) {
    return '$count小时前';
  }

  @override
  String daysAgo(int count) {
    return '$count天前';
  }

  @override
  String get sectionFavorites => '已保存的地点';

  @override
  String get addFavorite => '添加地点';

  @override
  String get favoriteLabelHint => '名称（例如：家、公司）';

  @override
  String get favoriteAddressHint => '地址';

  @override
  String get favoriteGeocodingError => '未找到该地址，请尝试更精确的地址。';

  @override
  String get trafficAlertTitle => '路线上有新交通状况';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$category于$age在您的路线上报告。\n\n是否要寻找替代路线？';
  }

  @override
  String get trafficContinue => '继续';

  @override
  String get trafficRecalculate => '重新计算路线';

  @override
  String get navExitTitle => '退出导航？';

  @override
  String get navExitBody => '是否要停止导航并返回地图？';

  @override
  String get navContinue => '继续导航';

  @override
  String get navExit => '是，退出';

  @override
  String get loadingInfo => '正在加载信息…';

  @override
  String get conditionsOnRoute => '路线状况';

  @override
  String get calculateRoute => '计算路线';

  @override
  String get sectionNavigationVoice => '导航语音';

  @override
  String get voiceGuidance => '语音引导';

  @override
  String get voiceGuidanceDesc => '导航时朗读转弯指示';

  @override
  String get testVoiceEngine => '测试语音引擎';

  @override
  String get testVoiceEngineDesc => '点击以检查 TTS 引擎并获取设置说明';

  @override
  String get ttsDialogTitle => '缺少语音引擎';

  @override
  String get ttsDialogBody =>
      '未找到可用的文本转语音引擎。\n\nRoadstr 仅依赖开源软件——请从 F-Droid 安装以下免费引擎之一：';

  @override
  String get ttsRhvoiceDesc => '声音自然，支持语言有限';

  @override
  String get ttsEspeakDesc => '支持 100 多种语言，声音机械化';

  @override
  String get ttsInstallNote =>
      '⚠️ 安装后：\n1. Android 设置 → 无障碍 → 文字转语音输出\n2. 选择刚安装的引擎\n3. 下载您所用语言的语音数据\n4. 完全重启 Roadstr';

  @override
  String get ttsTestNow => '立即测试';

  @override
  String get voiceUnsupportedTitle => '语音导航不可用';

  @override
  String get voiceUnsupportedBody => '您的语言目前尚不支持语音转弯指示。导航说明仍会以文字形式显示在屏幕上。';

  @override
  String get kokoroModelTitle => '语音模型 (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => '未下载 · 约82 MB';

  @override
  String get kokoroModelStatusDownloading => '下载中...';

  @override
  String get kokoroModelStatusReady => '语音模型就绪';

  @override
  String get kokoroModelDownloadBtn => '下载';

  @override
  String get kokoroModelSupportedLangs => '支持：意大利语、英语、西班牙语、法语、日语、中文、葡萄牙语';

  @override
  String get autoDarkMode => '自动深色主题';

  @override
  String get autoDarkModeDesc => '日落时自动启用深色主题';

  @override
  String get settingsImperialUnits => '英制单位';

  @override
  String get settingsImperialUnitsDesc => '使用英里和英尺代替公里和米';

  @override
  String get arrivedTitle => '🎉 您已到达！';

  @override
  String get arrivedBody => '您已到达目的地。';

  @override
  String get arrivedFeedbackPrompt => '旅途如何？';

  @override
  String get feedbackBad => '不好';

  @override
  String get feedbackGood => '很好！';

  @override
  String get feedbackDialogTitle => '告诉我们哪里出了问题';

  @override
  String get feedbackHint => '描述问题…';

  @override
  String get feedbackSent => '反馈已发送 — 谢谢！ 🙏';

  @override
  String get feedbackSubmit => '发送';

  @override
  String get transportModeCar => '驾车';

  @override
  String get transportModeWalk => '步行';

  @override
  String etaArrivalLabel(String time) {
    return '到达 $time';
  }

  @override
  String get supportRoadstr => '支持 Roadstr';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address 已复制到剪贴板';
  }

  @override
  String get disclaimerTitle => '重要提示';

  @override
  String get disclaimerAccept => '我已阅读并接受';

  @override
  String get disclaimerBody =>
      'Roadstr是一款基于OpenStreetMap数据和Nostr协议的实验性开源社区维护导航应用程序，可供任何国家的用户使用。下载、安装或使用本应用程序即表示用户无条件接受以下全部条款，不受地域限制。\n\n🚗 道路安全第一\n驾驶员必须始终注视道路，并遵守所有适用的交通法规及现场交通标志，二者始终优先于本应用程序发出的任何指示。切勿在驾驶过程中操作本设备；应在出发前将设备固定于经认可且清晰可见的支架上，且在车辆行驶过程中，切勿因操作设备而分散对道路的注意力。\n\n⚠️ 风险自担——全球适用\n用户在任何国家、任何法律体系下使用Roadstr，即表示已明知并自愿承担与使用本应用程序相关的一切风险，包括但不限于：交通事故、人身伤害、死亡、财产损失、车辆损坏、罚款、行政处罚、拖车、车辆扣押、刑事责任，或因依赖本应用程序而直接或间接产生的任何其他后果。用户对每一项驾驶及导航决策承担全部且唯一的责任。\n\n🚫 不提供任何担保\nRoadstr严格按照\"现状\"及\"可用性现状\"提供，不附带任何明示、默示或法定的担保，包括但不限于关于准确性、完整性、可靠性、可用性、适销性、特定用途适用性及不侵权的担保。地图数据、路线计算、限速信息、测速摄像头以及限行区域（ZTL/ZAC/LTZ）信息均来自开放的社区维护数据源（OpenStreetMap、Overpass API），这些数据在任何国家、地区或市镇、任何时间均可能不完整、过时或不准确，且恕不另行通知。用户应自行负责，在出行前及出行过程中，依据当地官方标志和法规独立核实所建议路线的合法性及可通行性。\n\n📍 精度与GPS\nGPS定位可能不准确或不可用。所有方向指引、距离及提醒信息仅供参考，绝不可作为驾驶决策的唯一依据。\n\n🛡️ 责任限制\n在任何司法管辖区适用法律允许的最大范围内，开发者、贡献者以及参与创建或分发Roadstr的任何一方，均不对因使用或无法使用本应用程序而引起或与之相关的任何直接、间接、附带、后果性、特殊、惩戒性或惩罚性损害（包括人身伤害、死亡或经济损失）承担责任，即使已被告知可能发生此类损害亦然。若某一司法管辖区不允许全部或部分上述责任限制，则责任应在该司法管辖区法律所允许的最小范围内予以限制。\n\n🤝 赔偿\n用户同意就因其使用本应用程序或违反本条款而产生的任何索赔、损害、损失或费用（包括律师费），对开发者及贡献者进行赔偿并使其免受损害。\n\n🔒 隐私\n本应用程序不会将位置数据传输至Roadstr自身的服务器。路线计算通过第三方服务（OSRM、GraphHopper、OpenRouteService）完成，仅向其发送起点和终点坐标。\n\n⚖️ 可分割性\n若本条款中的任何条文在特定司法管辖区被认定为不可执行，该条文应在必要的最小范围内予以限制或删除，其余条文的完整效力及有效性不受影响。\n\n无论在世界任何地方使用Roadstr，用户均确认已阅读、理解并无条件接受上述全部条款，并对本应用程序的使用及由此产生的任何后果承担全部且完整的责任——及全部风险。';

  @override
  String get readOnWikipedia => '在维基百科上阅读';

  @override
  String searchOnEngine(String engine) {
    return '在$engine上搜索';
  }

  @override
  String get plannerFromHint => '从…';

  @override
  String get plannerToHint => '目的地…';

  @override
  String departEta(String dep, String arr) {
    return '出发 $dep  →  预计到达 $arr';
  }

  @override
  String get modeCar => '轿车';

  @override
  String get modeBike => '自行车';

  @override
  String get modeWalk => '步行';

  @override
  String windSpeed(String speed) {
    return '风速 $speed km/h';
  }

  @override
  String durationMin(int m) {
    return '$m分钟';
  }

  @override
  String durationHourMin(int h, int m) {
    return '$h小时$m分';
  }

  @override
  String get weatherClear => '晴天';

  @override
  String get weatherPartlyCloudy => '多云';

  @override
  String get weatherCloudy => '阴天';

  @override
  String get weatherFog => '雾';

  @override
  String get weatherLightRain => '小雨';

  @override
  String get weatherRain => '雨';

  @override
  String get weatherSnow => '雪';

  @override
  String get weatherShowers => '阵雨';

  @override
  String get weatherThunderstorm => '雷雨';

  @override
  String get ztlAheadWarning => '前方为限行区,路线将进入该区域';

  @override
  String get ztlInsideWarning => '限行区';

  @override
  String get onboardingAppSubtitle => '开源 Nostr 导航';

  @override
  String get onboardingWelcomeTitle => '欢迎';

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
  String get onboardingGetStarted => '开始';

  @override
  String get onboardingNostrTitle => 'Nostr 身份';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => '已连接';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55（推荐）';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec 密钥';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError => 'nsec 密钥无效 — 请检查后重试。';

  @override
  String get onboardingSkip => '暂时跳过';

  @override
  String get onboardingContinue => '继续';

  @override
  String get onboardingEnterNsec => '输入 nsec 密钥';

  @override
  String get onboardingSetupTitle => '设置 Roadstr';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => '位置';

  @override
  String get onboardingLocationGranted => '已授予位置权限';

  @override
  String get onboardingLocationRequired => 'GPS 导航所必需';

  @override
  String get onboardingGrantButton => '授予';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS 用户';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'AI 语音导航（可选）';

  @override
  String get onboardingVoiceReady => 'Kokoro 语音模型已就绪';

  @override
  String get onboardingVoiceDownloading => '正在下载语音模型…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => '下载';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => '您已准备好导航！';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => '出发！';

  @override
  String get onboardingProfileLoading => '正在加载个人资料…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => '语音';

  @override
  String get kokoroVoiceFemale => '女声';

  @override
  String get kokoroVoiceMale => '男声';

  @override
  String get kokoroVoiceGenderUnavailable => '该语言暂无男声';

  @override
  String get kokoroSpeedTitle => '语速';

  @override
  String get onboardingFavoritesSyncNotice =>
      '可选：您保存的收藏可以通过Nostr中继在您的设备之间同步，使用您自己的密钥进行端到端加密（NIP-44）— 中继只能看到密文，除您之外没有人能读取内容。可随时在设置中启用。';

  @override
  String get parkingSaveHere => '在此保存停车位置';

  @override
  String get parkingMarkerTitle => '停车位置';

  @override
  String get parkingNavigateHere => '导航到停车位置';

  @override
  String get parkingRemove => '删除停车位置';

  @override
  String get parkingSavedSnack => '停车位置已保存';

  @override
  String get parkingRemovedSnack => '停车位置已删除';

  @override
  String get exportFavoritesTitle => '导出收藏';

  @override
  String get exportFavoritesDesc => '将您收藏的地点保存为JSON文件，可用于备份或转移到其他设备。';

  @override
  String get exportEncryptToggle => '使用密码加密';

  @override
  String get exportPasswordHint => '密码';

  @override
  String get exportButton => '导出';

  @override
  String get exportSuccessSnack => '收藏已导出';

  @override
  String get exportFailedSnack => '导出失败';

  @override
  String get importFavoritesTitle => '导入收藏';

  @override
  String get importPasswordPrompt => '此文件已加密 — 请输入密码';

  @override
  String importSuccessSnack(int n) {
    return '已导入$n个收藏';
  }

  @override
  String get importFailedSnack => '导入失败 — 请检查文件和密码';

  @override
  String get syncFavoritesTitle => '同步收藏';

  @override
  String get syncFavoritesDesc =>
      '将您的收藏以端到端加密方式发布到您的Nostr中继，让它们在所有设备上跟随您。中继只能看到密文 — 除您之外没有人能读取内容。';

  @override
  String get syncNowButton => '推送到Nostr';

  @override
  String get syncPullButton => '从Nostr拉取';

  @override
  String get syncSuccessSnack => '收藏已同步';

  @override
  String get syncFailedSnack => '同步失败';

  @override
  String syncLastSyncLabel(String when) {
    return '上次同步：$when';
  }

  @override
  String get syncNoIdentity => '使用Nostr登录（设置 → 个人资料）以启用同步';

  @override
  String get onboardingVpnNotice =>
      '为了最大程度保护隐私——路况报告会在 Nostr 网络中传播——建议使用 VPN。推荐使用可用比特币付款的 Mullvad。';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      '为确保应用正常运行，请将位置权限设置为“始终允许”，而不仅是使用应用时允许。';

  @override
  String get trafficNormal => '交通正常';

  @override
  String get trafficModerate => '交通缓行';

  @override
  String get trafficHeavy => '交通拥堵';

  @override
  String get avoidHighwaysChip => '避开高速公路';

  @override
  String get avoidTollsChip => '避开收费道路';

  @override
  String get preferShorterChip => '最短路线';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => '显示路线预览';
}
