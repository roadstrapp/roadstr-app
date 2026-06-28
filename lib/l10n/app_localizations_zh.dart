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
  String get sectionPrivacy => '隐私';

  @override
  String get sectionInfo => '信息';

  @override
  String get sectionLanguage => '语言';

  @override
  String get themeLightNostr => '浅色 · Nostr紫';

  @override
  String get themeLightBitcoin => '浅色 · 比特币橙';

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
  String get privacyMode => '隐私模式';

  @override
  String get privacyModeDescription => '不发送匿名遥测数据';

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
}
