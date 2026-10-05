package app.roadstr.core.discovery.lexicon

/**
 * Search vocabulary: Japanese. Written from general knowledge; a native speaker should review it.
 * Japanese has no spaces, so each character is a token; "<place>の<thing>" puts the place first.
 */
internal object LexiconJa {
    const val TEXT = """
c RESTAURANT: レストラン, 食堂, 飲食店, 料理店
c FAST_FOOD: ファストフード
c CAFE: カフェ, 喫茶店, コーヒーショップ, 喫茶
c BAR: バー, 居酒屋
c PUB: パブ
c SUPERMARKET: スーパー, スーパーマーケット, 食料品店
c CONVENIENCE: コンビニ, コンビニエンスストア
c PHARMACY: 薬局, ドラッグストア, 薬屋
c FUEL: ガソリンスタンド, 給油所, ガソスタ, ガソリン
c CHARGING_STATION: 充電スタンド, 充電器, 充電ステーション, 急速充電, 充電所
c PARKING: 駐車場, パーキング, コインパーキング
c HOTEL: ホテル, 旅館, 宿, 宿泊, 民宿, ゲストハウス, ホステル
c HOSPITAL: 病院, 救急, 救急病院
c ATM: atm, 現金自動預け払い機
c BANK: 銀行, 信用金庫
c POST_OFFICE: 郵便局
c POLICE: 警察, 警察署, 交番
c CINEMA: 映画館, シネマ, ミニシアター
c TRAIN_STATION: 駅, 鉄道駅
a VEGAN: ヴィーガン, ビーガン, 完全菜食
a VEGETARIAN: ベジタリアン, 菜食, 菜食主義
a GLUTEN_FREE: グルテンフリー, 小麦不使用
a OPEN_24_7: 24時間, 二十四時間, 24 7
u PIZZA: ピザ, ピザ店, ピッツェリア
u SUSHI: 寿司, すし, 鮨
near_me: 近く, 近所, この辺, 現在地周辺, 現在地付近, 一番近い, 最寄り, 最寄の
open_now: 営業中, 今開いている, 開いている, 今やってる, 今営業, 営業している
in_after: の, で, にある
near_after: の近く, の周辺, の付近, のそば, の隣, の横, 周辺, 付近
near_dest: 目的地の近く, 目的地周辺, 目的地付近, 目的地近く
route: ルート沿い, 経路上, ルート上, 途中, 道中, 道沿い, 沿道
filler: を, が, は, と, や, に, も, へ, ください, 探して, 探す, 教えて, 行きたい, おすすめ, 美味しい, 安い, 人気, 良い, ある, 欲しい, 検索
"""
}
