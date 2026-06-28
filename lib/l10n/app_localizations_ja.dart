// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Japanese (`ja`).
class AppLocalizationsJa extends AppLocalizations {
  AppLocalizationsJa([String locale = 'ja']) : super(locale);

  @override
  String get searchHint => 'どこに行きますか？';

  @override
  String get calculatingRoute => 'ルートを計算中…';

  @override
  String get routeNotFoundTitle => 'ルートが見つかりません';

  @override
  String get noRouteFound => 'ルートが見つかりません。接続を確認してください。';

  @override
  String get emptyServerResponse => 'サーバーの応答が空です。接続を確認してください。';

  @override
  String routeError(String error) {
    return 'ルート計算エラー：$error';
  }

  @override
  String get gpsNotAvailable =>
      'GPS利用不可 — Settings → App → Roadstr → Permissions → Location';

  @override
  String get acquiringGps => 'GPS取得中…';

  @override
  String get graphhopperServerNotConfigured =>
      'GraphHopperサーバーが設定されていません — OSRMを使用';

  @override
  String get graphhopperApiKeyNotConfigured =>
      'GraphHopper APIキーが設定されていません — OSRMを使用';

  @override
  String get openrouteApiKeyNotConfigured =>
      'OpenRouteService APIキーが設定されていません — OSRMを使用';

  @override
  String get chooseRoute => 'ルートを選択';

  @override
  String routeOptionsCount(int count) {
    return '$countつの選択肢';
  }

  @override
  String get cancel => 'キャンセル';

  @override
  String get startNavigation => 'ナビゲーション開始';

  @override
  String get fastestRoute => '最速';

  @override
  String get now => '今';

  @override
  String get history => '履歴';

  @override
  String get clearHistory => 'クリア';

  @override
  String get selectedPosition => '選択した位置';

  @override
  String get bottomBarProfile => 'プロフィール';

  @override
  String get bottomBarMenu => 'メニュー';

  @override
  String get settingsTitle => '設定';

  @override
  String get sectionTheme => 'テーマ';

  @override
  String get sectionMap => 'マップ';

  @override
  String get sectionPrivacy => 'プライバシー';

  @override
  String get sectionInfo => '情報';

  @override
  String get sectionLanguage => '言語';

  @override
  String get themeLightNostr => 'ライト · Nostrバイオレット';

  @override
  String get themeLightBitcoin => 'ライト · ビットコインオレンジ';

  @override
  String get langSystem => 'システムデフォルト';

  @override
  String get langItalian => 'Italiano';

  @override
  String get langEnglish => 'English';

  @override
  String get keepScreenOn => '画面をオンに保つ';

  @override
  String get keepScreenOnDescription => 'ナビゲーション中にスリープを防止';

  @override
  String get rotateMap => 'マップが方向に追従';

  @override
  String get rotateMapDescription => '走行方向に基づいてマップが回転';

  @override
  String get mapTileUrlLabel => 'マップタイルURL';

  @override
  String get routingProviderLabel => 'ルーティングプロバイダー';

  @override
  String get osrmProvider => 'OSRM（公共、キー不要）';

  @override
  String get graphhopperLocalProvider => 'GraphHopper（ローカル/プライベート）';

  @override
  String get graphhopperCloudProvider => 'GraphHopper Cloud（APIキー）';

  @override
  String get openrouteProvider => 'OpenRouteService（APIキー）';

  @override
  String get graphhopperServerHint => 'http://localhost:8989/route';

  @override
  String get graphhopperApiKeyHint => 'GraphHopper APIキー（任意）';

  @override
  String get verify => '確認';

  @override
  String get graphhopperServerUrlRequired => '確認前にサーバーURLを入力してください。';

  @override
  String get successTitle => '成功';

  @override
  String get graphhopperServerReachable => 'GraphHopperサーバーに到達可能';

  @override
  String get errorTitle => 'エラー';

  @override
  String get close => '閉じる';

  @override
  String get privacyMode => 'プライバシーモード';

  @override
  String get privacyModeDescription => '匿名テレメトリデータを送信しない';

  @override
  String get infoVersion => 'バージョン';

  @override
  String get infoProtocol => 'プロトコル';

  @override
  String get infoMaps => 'マップ';

  @override
  String get infoRouting => 'ルーティング';

  @override
  String get infoSource => 'ソース';

  @override
  String get providerOsrm => 'OSRM';

  @override
  String get providerGraphhopperSelfHosted => 'GraphHopper（セルフホスト）';

  @override
  String get providerGraphhopperCloud => 'GraphHopper（クラウド）';

  @override
  String get providerOpenroute => 'OpenRouteService';

  @override
  String get profileTitle => 'プロフィール';

  @override
  String get notConnected => '未接続';

  @override
  String get loginWithNostrTitle => 'NOSTRでログイン';

  @override
  String get amberNip55Title => 'Amber / NIP-55';

  @override
  String get amberLoginDescription => '秘密鍵がデバイスから外に出ることはありません · 推奨';

  @override
  String get nsecLoginOption => 'nsecを入力';

  @override
  String get nsecLoginDescription => '秘密鍵はローカルに保存 · セキュリティが低い';

  @override
  String get connectedViaAmber => 'Amber（NIP-55）経由で接続';

  @override
  String get connectedViaNsec => 'nsec経由で接続';

  @override
  String get publicKeyLabel => '公開鍵';

  @override
  String get npubCopiedToClipboard => 'npubをクリップボードにコピーしました';

  @override
  String get logoutTitle => '切断';

  @override
  String get logoutConfirmation => 'このデバイスからNostr認証情報を削除しますか？';

  @override
  String get logoutButton => '切断';

  @override
  String get nostrIdentityInfo =>
      'Nostrアイデンティティを持つことで、中央サーバーなしにNostrネットワーク上で分散型の方法で交通警告、レポート、興味のある場所を公開できます。';

  @override
  String get warningTitle => '警告';

  @override
  String get nsecWarning =>
      'あなたはNostrの秘密鍵をアプリに直接ぶち込もうとしています。デバイスへの物理的またはリモートアクセスができる人は誰でもそれを読み取り、あなたのNostrアイデンティティを永久に乗っ取ることができます。';

  @override
  String get amberSecureMethodHint =>
      '✦  安全な方法はAmber（NIP-55）です：nsecはアプリの署名ボールトから出ることはありません。';

  @override
  String get nsecRiskAcknowledgment => 'リスクを理解した上で続行します';

  @override
  String get continueButton => '続行';

  @override
  String get amberDialogTitle => 'Amber / NIP-55';

  @override
  String get amberDescription =>
      'AmberはNIP-55準拠のAndroidアプリ署名者です。秘密鍵はAmber内に隔離され、共有されることはありません。';

  @override
  String get requestKeyFromAmber => 'Amberから公開鍵をリクエスト';

  @override
  String get amberNotFound =>
      'Amberが見つかりません。Play Storeからインストールするか、npubを手動で入力してください。';

  @override
  String get waitingForAmberResponse => 'Amberの応答を待っています…';

  @override
  String get pasteNpubManually => 'npubを手動で貼り付け：';

  @override
  String get confirmNpub => 'npubを確認';

  @override
  String get enterNsecTitle => 'nsecを入力';

  @override
  String get loginButton => 'ログイン';

  @override
  String get invalidNpubTitle => '無効なnpub';

  @override
  String get invalidNsecTitle => '無効なnsec';

  @override
  String get invalidNpubMessage => '正しいnpubを貼り付けたか確認してください。';

  @override
  String get invalidNsecMessage => '正しいnsecを貼り付けたか確認してください。';

  @override
  String get amberResponseError => 'Amber応答エラー';

  @override
  String get ok => 'OK';

  @override
  String get or => 'または';

  @override
  String get gpsNotActiveTitle => 'GPSがアクティブではありません';

  @override
  String get gpsDisabledMessage =>
      '現在地を取得してナビゲーションを使用するには、デバイスの設定でGPSを有効にしてください。';

  @override
  String get openSettings => '設定';

  @override
  String get myLocation => '現在地';

  @override
  String get loginToReport => 'イベントを報告するにはNostrでログイン（プロフィールセクション）';

  @override
  String get navigateHere => 'ここへナビゲート';

  @override
  String get reportEventHere => 'ここでイベントを報告';

  @override
  String get zapSendSats => 'Zap ⚡（satsを送る）';

  @override
  String get sendZap => 'Zapを送る';

  @override
  String get chooseAmountSats => 'サトシ（sats）の金額を選択：';

  @override
  String get customAmount => 'カスタム金額…';

  @override
  String get zapSending => '送信中…';

  @override
  String zapSat(int sats) {
    return '⚡$sats sat';
  }

  @override
  String get fetchingLightningAddress => 'Lightningアドレスを取得中…';

  @override
  String get noLightningAddress => 'このレポーターにはLightningアドレスがありません';

  @override
  String get requestingInvoice => 'インボイスをリクエスト中…';

  @override
  String get lnurlUnavailable => 'LNURLが利用できません';

  @override
  String get invoiceFailed => 'インボイスを生成できません';

  @override
  String get openingWallet => 'ウォレットを開いています…';

  @override
  String get payingViaNwc => 'NWC経由で支払い中…';

  @override
  String get noLightningWallet => 'Lightningウォレットが見つかりません';

  @override
  String zapSent(int sats) {
    return '⚡ $sats sats送信しました！';
  }

  @override
  String get stillThere => 'まだあります';

  @override
  String get notThereAnymore => 'もうありません';

  @override
  String get loginToConfirm => '確認または反論するにはNostrでログイン';

  @override
  String get reportAnEvent => 'イベントを報告';

  @override
  String get optionalComment => '任意のコメント…';

  @override
  String get publish => '公開';

  @override
  String get publishing => '公開中…';

  @override
  String get reportPublished => 'レポートが公開されました ✓';

  @override
  String get myReports => 'マイレポート';

  @override
  String get noReportsYet => '公開されたレポートはありません';

  @override
  String get zapBalance => 'Zapバランス';

  @override
  String get satoshiFromReports => 'あなたのレポートから受け取ったサトシ';

  @override
  String get reputationHigh => '高';

  @override
  String get reputationMedium => '中';

  @override
  String get reputationLow => '低';

  @override
  String reputationLabel(String level) {
    return '評判 $level';
  }

  @override
  String reliability(int pct) {
    return '信頼性：$pct%';
  }

  @override
  String get confirmedLabel => '確認済み';

  @override
  String get removedLabel => '削除済み';

  @override
  String get positionLabel => '位置';

  @override
  String get loadingLabel => '読み込み中…';

  @override
  String get sectionWebSearch => 'ウェブ検索';

  @override
  String get sectionLightning => 'Lightning';

  @override
  String get nwcLabel => 'Nostr Wallet Connect（NWC）';

  @override
  String get nwcDesc =>
      'NWC URI（Alby Hub、Mutiny、Cashu…）を貼り付けて、アプリから直接Zapsを支払います。';

  @override
  String get searchEngineQwantDesc => '欧州、プライバシー優先。追跡なし、広告プロファイルなし。推奨。';

  @override
  String get searchEngineBraveDesc =>
      '独立したインデックス、オープンソース。GoogleやBingに依存しない。プロファイリングゼロ。';

  @override
  String get searchEngineDdgDesc => 'プライバシー重視で人気。結果は部分的にBingから — 覚えておいてください。';

  @override
  String get searchEngineStartpageDesc => 'Googleの結果を、Googleにデータを渡さずに。合理的な妥協。';

  @override
  String get searchEngineGoogleDesc =>
      '非常に効果的。でも覚えておいて：Googleはあなたのお母さんよりあなたのことを知っていて、すべてを広告主に売っています。まあ、あなたの選択です。🍪';

  @override
  String get categoryPolice => '警察';

  @override
  String get categorySpeedCamera => '速度測定カメラ';

  @override
  String get categoryTrafficJam => '渋滞';

  @override
  String get categoryAccident => '事故';

  @override
  String get categoryRoadClosure => '通行止め';

  @override
  String get categoryConstruction => '工事';

  @override
  String get categoryHazard => '危険';

  @override
  String get categoryRoadCondition => '道路状況';

  @override
  String get categoryPothole => '路面の穴';

  @override
  String get categoryFog => '霧';

  @override
  String get categoryIce => '氷';

  @override
  String get categoryAnimal => '動物';

  @override
  String get categoryOther => 'その他';

  @override
  String get dateTimeLabel => '日付 / 時刻';

  @override
  String minutesAgo(int count) {
    return '$count分前';
  }

  @override
  String hoursAgo(int count) {
    return '$count時間前';
  }

  @override
  String daysAgo(int count) {
    return '$count日前';
  }

  @override
  String get sectionFavorites => '保存した場所';

  @override
  String get addFavorite => '場所を追加';

  @override
  String get favoriteLabelHint => '名前（例：自宅、職場）';

  @override
  String get favoriteAddressHint => '住所';

  @override
  String get favoriteGeocodingError => '住所が見つかりません。より具体的な住所をお試しください。';
}
