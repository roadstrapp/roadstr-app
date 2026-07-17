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
  String get sectionInfo => '情報';

  @override
  String get sectionLanguage => '言語';

  @override
  String get themeLightNostr => 'ライト · Nostrバイオレット';

  @override
  String get themeLightBitcoin => 'ライト · ビットコインオレンジ';

  @override
  String get themeDarkNostr => 'ダーク · Nostrバイオレット';

  @override
  String get themeDarkBitcoin => 'ダーク · ビットコインオレンジ';

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

  @override
  String get trafficAlertTitle => 'ルート上の新しい交通情報';

  @override
  String trafficAlertBody(Object age, Object category) {
    return '$categoryが$ageにルート上で報告されました。\n\n代替ルートを探しますか？';
  }

  @override
  String get trafficContinue => '続ける';

  @override
  String get trafficRecalculate => 'ルートを再計算';

  @override
  String get navExitTitle => 'ナビゲーションを終了しますか？';

  @override
  String get navExitBody => 'ナビゲーションを停止してマップに戻りますか？';

  @override
  String get navContinue => 'ナビゲーションを続ける';

  @override
  String get navExit => 'はい、終了';

  @override
  String get loadingInfo => '情報を読み込んでいます…';

  @override
  String get conditionsOnRoute => 'ルートの状況';

  @override
  String get calculateRoute => 'ルートを計算';

  @override
  String get sectionNavigationVoice => 'ナビゲーション音声';

  @override
  String get voiceGuidance => '音声案内';

  @override
  String get voiceGuidanceDesc => 'ナビゲーション中に曲がる指示を音声で読み上げます';

  @override
  String get testVoiceEngine => '音声エンジンをテスト';

  @override
  String get testVoiceEngineDesc => 'タップしてTTSエンジンを確認し、設定手順を取得';

  @override
  String get ttsDialogTitle => '音声エンジンがありません';

  @override
  String get ttsDialogBody =>
      '動作するText-to-Speechエンジンが見つかりませんでした。\n\nRoadstrはオープンソースソフトウェアのみに依存しています — F-Droidからこれらの無料エンジンのいずれかをインストールしてください：';

  @override
  String get ttsRhvoiceDesc => '自然な音声、対応言語は限定的';

  @override
  String get ttsEspeakDesc => '100以上の言語に対応、機械的な音声';

  @override
  String get ttsInstallNote =>
      '⚠️ インストール後：\n1. Android設定 → ユーザー補助 → テキスト読み上げの出力\n2. インストールしたばかりのエンジンを選択\n3. お使いの言語の音声データをダウンロード\n4. Roadstrを完全に再起動';

  @override
  String get ttsTestNow => '今すぐテスト';

  @override
  String get voiceUnsupportedTitle => '音声案内は利用できません';

  @override
  String get voiceUnsupportedBody =>
      'お使いの言語は音声によるナビゲーション案内にまだ対応していません。ナビゲーションの指示は引き続き画面にテキストで表示されます。';

  @override
  String get kokoroModelTitle => '音声モデル (Kokoro AI)';

  @override
  String get kokoroModelStatusNotDownloaded => '未ダウンロード · 約82 MB';

  @override
  String get kokoroModelStatusDownloading => 'ダウンロード中...';

  @override
  String get kokoroModelStatusReady => '音声モデル準備完了';

  @override
  String get kokoroModelDownloadBtn => 'ダウンロード';

  @override
  String get kokoroModelSupportedLangs =>
      '対応言語：イタリア語、英語、スペイン語、フランス語、日本語、中国語、ポルトガル語';

  @override
  String get autoDarkMode => '自動ダークテーマ';

  @override
  String get autoDarkModeDesc => '日没時にダークテーマを有効にします';

  @override
  String get settingsImperialUnits => 'ヤード・ポンド法';

  @override
  String get settingsImperialUnitsDesc => 'キロメートル・メートルの代わりにマイル・フィートを使用';

  @override
  String get arrivedTitle => '🎉 到着しました！';

  @override
  String get arrivedBody => '目的地に到着しました。';

  @override
  String get arrivedFeedbackPrompt => 'いかがでしたか？';

  @override
  String get feedbackBad => '悪い';

  @override
  String get feedbackGood => '良い！';

  @override
  String get feedbackDialogTitle => '何が問題だったか教えてください';

  @override
  String get feedbackHint => '問題を説明してください…';

  @override
  String get feedbackSent => 'フィードバックを送信しました — ありがとう！ 🙏';

  @override
  String get feedbackSubmit => '送信';

  @override
  String get transportModeCar => '車';

  @override
  String get transportModeWalk => '徒歩';

  @override
  String etaArrivalLabel(String time) {
    return '着 $time';
  }

  @override
  String get supportRoadstr => 'Roadstrをサポート';

  @override
  String lightningAddressCopied(String address) {
    return '⚡ $address をクリップボードにコピーしました';
  }

  @override
  String get disclaimerTitle => '重要なお知らせ';

  @override
  String get disclaimerAccept => '読んで同意します';

  @override
  String get disclaimerBody =>
      'Roadstrは、OpenStreetMapのデータおよびNostrプロトコルに基づく、実験的でオープンソースのコミュニティ運営によるナビゲーションアプリケーションであり、あらゆる国での利用を目的として提供されています。本アプリケーションをダウンロード、インストール、または使用することにより、利用者は地域的な制限なく、以下のすべての条件を無条件に承諾したものとみなされます。\n\n🚗 交通安全を最優先に\n運転者は常に前方の道路に注意を払い、適用されるすべての交通法規および設置された標識を遵守しなければなりません。これらは常に本アプリケーションのいかなる指示にも優先します。運転中に本デバイスを操作することは決して行わないでください。走行を開始する前に、承認された視認性の高いマウントに固定し、車両が走行中は道路から注意をそらして本デバイスを操作することのないようにしてください。\n\n⚠️ 危険の引受け — 世界共通\n利用者は、いかなる国、いかなる法制度のもとであっても、Roadstrを使用することにより、その使用に関連するあらゆるリスク——交通事故、人身傷害、死亡、財産的損害、車両損害、罰金、行政処分、レッカー移動、車両の押収、刑事責任、または本アプリケーションへの依拠に直接的または間接的に起因するその他のあらゆる結果を含むがこれらに限定されない——を、認識した上で自らの意思により引き受けるものとします。運転および経路選択に関するすべての判断について、利用者のみが全責任を負うものとします。\n\n🚫 保証の否認\nRoadstrは、明示、黙示または法定を問わず、正確性、完全性、信頼性、可用性、商品性、特定目的への適合性、権利の非侵害を含むがこれらに限定されないいかなる保証もなく、厳密に「現状有姿」および「提供可能な限りにおいて」提供されます。地図データ、経路案内、制限速度、速度取締装置(オービス)、および交通制限区域(ZTL/ZAC/LTZ)に関する情報は、OpenStreetMapやOverpass APIなど、コミュニティによって運営されるオープンなデータソースに由来するものであり、国、地域または自治体を問わず、予告なくいつでも不完全、古い情報、または不正確である可能性があります。利用者は、提案された経路の適法性および通行可能性について、出発前および走行中に、現地の公式な標識および規則に照らして自ら独自に確認する責任を単独で負うものとします。\n\n📍 精度およびGPSについて\nGPSによる位置測定は、不正確または利用できない場合があります。すべての経路案内、距離表示およびアラートは目安としてのみ提供されるものであり、運転上の判断の唯一の根拠として依拠してはなりません。\n\n🛡️ 責任の制限\n適用法によって認められる最大限の範囲において、開発者、貢献者、およびRoadstrの作成または配布に関与したその他の当事者は、人身傷害、死亡または金銭的損失を含む、本アプリケーションの使用または使用不能に起因もしくは関連して生じた直接的、間接的、付随的、結果的、特別、懲罰的またはその他いかなる種類の損害についても、たとえそのような損害が生じる可能性を事前に知らされていた場合であっても、一切の責任を負わないものとします。当該制限の全部または一部を認めない法域においては、責任はその法域の法律で認められる最小限の範囲に制限されるものとします。\n\n🤝 補償\n利用者は、本アプリケーションの利用または本条件への違反に起因して生じた一切の請求、損害、損失または費用(弁護士費用を含む)について、開発者および貢献者を補償し、免責するものとします。\n\n🔒 プライバシー\n位置情報がRoadstr自身のサーバーに送信されることはありません。経路計算は第三者サービス(OSRM、GraphHopper、OpenRouteService)を通じて行われ、これらのサービスには出発地および目的地の座標のみが送信されます。\n\n⚖️ 分離可能性\n本条件のいずれかの条項が、特定の法域において執行不能と判断された場合、当該条項は必要最小限の範囲で制限または分離されるものとし、残りの条項はすべて完全な効力を保持するものとします。\n\n世界のどこであれ、Roadstrを使用することにより、利用者は上記のすべての条件を読み、理解し、無条件に承諾したことを確認し、本アプリケーションの使用およびそれに起因するあらゆる結果について、完全かつ全面的な責任——およびあらゆるリスク——を負うものとします。';

  @override
  String get readOnWikipedia => 'Wikipediaで読む';

  @override
  String searchOnEngine(String engine) {
    return '$engineで検索';
  }

  @override
  String get plannerFromHint => '出発地…';

  @override
  String get plannerToHint => '目的地…';

  @override
  String departEta(String dep, String arr) {
    return '出発 $dep  →  到着 $arr';
  }

  @override
  String get modeCar => '車';

  @override
  String get modeBike => '自転車';

  @override
  String get modeWalk => '徒歩';

  @override
  String windSpeed(String speed) {
    return '風速 $speed km/h';
  }

  @override
  String durationMin(int m) {
    return '$m分';
  }

  @override
  String durationHourMin(int h, int m) {
    return '$h時間$m分';
  }

  @override
  String get weatherClear => '晴れ';

  @override
  String get weatherPartlyCloudy => 'くもりがち';

  @override
  String get weatherCloudy => '曇り';

  @override
  String get weatherFog => '霧';

  @override
  String get weatherLightRain => '小雨';

  @override
  String get weatherRain => '雨';

  @override
  String get weatherSnow => '雪';

  @override
  String get weatherShowers => 'にわか雨';

  @override
  String get weatherThunderstorm => '雷雨';

  @override
  String get ztlAheadWarning => 'この先に交通制限区域があります。ルートはその中を通ります';

  @override
  String get ztlInsideWarning => '交通制限区域';

  @override
  String get onboardingAppSubtitle => 'オープンソース Nostr ナビゲーション';

  @override
  String get onboardingWelcomeTitle => 'ようこそ';

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
  String get onboardingGetStarted => 'はじめる';

  @override
  String get onboardingNostrTitle => 'Nostr ID';

  @override
  String get onboardingNostrSubtitle =>
      'Optional — connect to report road events, confirm alerts and earn Lightning tips.';

  @override
  String get onboardingNostrConnected => '接続済み';

  @override
  String get onboardingAmberTitle => 'Amber — NIP-55（推奨）';

  @override
  String get onboardingAmberSubtitle =>
      'Connect with the Amber signer app. Your private key never leaves Amber.';

  @override
  String get onboardingNsecTitle => 'nsec キー';

  @override
  String get onboardingNsecSubtitle =>
      'Paste your private key. Stored in the Android Keystore.';

  @override
  String get onboardingNsecError => 'nsec キーが無効です — 確認して再試行してください。';

  @override
  String get onboardingSkip => '後で設定する';

  @override
  String get onboardingContinue => '続ける';

  @override
  String get onboardingEnterNsec => 'nsec キーを入力';

  @override
  String get onboardingSetupTitle => 'Roadstr を設定';

  @override
  String get onboardingSetupSubtitle =>
      'Configure location access and optional voice guidance.';

  @override
  String get onboardingLocationTitle => '位置情報';

  @override
  String get onboardingLocationGranted => '位置情報へのアクセスが許可されました';

  @override
  String get onboardingLocationRequired => 'GPS ナビゲーションに必要';

  @override
  String get onboardingGrantButton => '許可';

  @override
  String get onboardingGrapheneTitle => 'GrapheneOS ユーザー';

  @override
  String get onboardingGrapheneBody =>
      'Grant Precise location (not Approximate) AND allow access Always (not only while in use) in:\nSettings → Apps → Roadstr → Permissions → Location\n\nWith only approximate or \"while in use\" location, GPS navigation will lose position in the background.';

  @override
  String get onboardingVoiceTitle => 'AI 音声ガイダンス（オプション）';

  @override
  String get onboardingVoiceReady => 'Kokoro 音声モデルが準備完了';

  @override
  String get onboardingVoiceDownloading => '音声モデルをダウンロード中…';

  @override
  String get onboardingVoiceNotDownloaded =>
      'Download the 82 MB Kokoro AI model for on-device voice';

  @override
  String get onboardingVoiceChecking => 'Checking model status…';

  @override
  String get onboardingDownloadButton => 'ダウンロード';

  @override
  String get onboardingVoiceLaterHint =>
      'You can also download the voice model later from\nSettings → Navigation voice.';

  @override
  String get onboardingReadyTitle => 'ナビゲーションの準備ができました！';

  @override
  String get onboardingReadyBody =>
      'Roadstr will now open the map.\nYou can configure everything else in Settings.';

  @override
  String get onboardingLetsGo => 'さあ出発！';

  @override
  String get onboardingProfileLoading => 'プロフィールを読み込み中…';

  @override
  String get onboardingNsecHint => 'nsec1…';

  @override
  String get kokoroVoiceGenderTitle => '音声';

  @override
  String get kokoroVoiceFemale => '女性';

  @override
  String get kokoroVoiceMale => '男性';

  @override
  String get kokoroVoiceGenderUnavailable => 'この言語では男性の音声は利用できません';

  @override
  String get kokoroSpeedTitle => '読み上げ速度';

  @override
  String get onboardingFavoritesSyncNotice =>
      '任意: 保存したお気に入りは、Nostrリレーを通じて端末間で同期できます。自分の鍵でエンドツーエンド暗号化（NIP-44）されるため、リレーには暗号文しか見えず、あなた以外は内容を読めません。設定からいつでも有効にできます。';

  @override
  String get parkingSaveHere => 'ここに駐車位置を保存';

  @override
  String get parkingMarkerTitle => '駐車位置';

  @override
  String get parkingNavigateHere => '駐車位置へナビ';

  @override
  String get parkingRemove => '駐車位置を削除';

  @override
  String get parkingSavedSnack => '駐車位置を保存しました';

  @override
  String get parkingRemovedSnack => '駐車位置を削除しました';

  @override
  String get exportFavoritesTitle => 'お気に入りをエクスポート';

  @override
  String get exportFavoritesDesc =>
      'お気に入りの場所をJSONファイルに保存して、バックアップや別の端末への移行に使えます。';

  @override
  String get exportEncryptToggle => 'パスワードで暗号化';

  @override
  String get exportPasswordHint => 'パスワード';

  @override
  String get exportButton => 'エクスポート';

  @override
  String get exportSuccessSnack => 'お気に入りをエクスポートしました';

  @override
  String get exportFailedSnack => 'エクスポートに失敗しました';

  @override
  String get importFavoritesTitle => 'お気に入りをインポート';

  @override
  String get importPasswordPrompt => 'このファイルは暗号化されています — パスワードを入力してください';

  @override
  String importSuccessSnack(int n) {
    return '$n件のお気に入りをインポートしました';
  }

  @override
  String get importFailedSnack => 'インポートに失敗しました — ファイルとパスワードを確認してください';

  @override
  String get syncFavoritesTitle => 'お気に入りを同期';

  @override
  String get syncFavoritesDesc =>
      'お気に入りをエンドツーエンド暗号化してNostrリレーに公開し、すべての端末で利用できます。リレーには暗号文しか見えません — あなた以外は内容を読めません。';

  @override
  String get syncNowButton => 'Nostrへ送信';

  @override
  String get syncPullButton => 'Nostrから取得';

  @override
  String get syncSuccessSnack => 'お気に入りを同期しました';

  @override
  String get syncFailedSnack => '同期に失敗しました';

  @override
  String syncLastSyncLabel(String when) {
    return '最終同期: $when';
  }

  @override
  String get syncNoIdentity => '同期を有効にするにはNostrでログインしてください（設定 → プロフィール）';

  @override
  String get onboardingVpnNotice =>
      '最大限のプライバシーのために — 報告はNostrネットワークに伝播されます — VPNの使用を推奨します。ビットコインで支払えるMullvadがおすすめです。';

  @override
  String get onboardingGrapheneAlwaysAllow =>
      '確実に動作させるには、位置情報の権限を「アプリの使用中のみ」ではなく「常に許可」に設定してください。';

  @override
  String get trafficNormal => '通常の交通量';

  @override
  String get trafficModerate => 'やや混雑';

  @override
  String get trafficHeavy => '渋滞';

  @override
  String get avoidHighwaysChip => '高速道路を回避';

  @override
  String get avoidTollsChip => '有料道路を回避';

  @override
  String get preferShorterChip => '最短ルート';

  @override
  String zapAmountButton(int sats) {
    return 'Zap $sats sat';
  }

  @override
  String get showRoutePreview => 'ルートプレビューを表示';
}
