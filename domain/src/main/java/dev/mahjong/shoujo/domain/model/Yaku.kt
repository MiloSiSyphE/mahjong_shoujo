package dev.mahjong.shoujo.domain.model

/**
 * Enumeration of all standard Riichi Mahjong yaku.
 *
 * Each yaku carries its han value for closed and open hands.
 * Yakuman yaku use han = YAKUMAN_HAN as a sentinel.
 */
enum class Yaku(
    val japaneseName: String,
    val englishName: String,
    val closedHan: Int,
    /** -1 means the yaku is only valid closed (e.g. riichi, tsumo). */
    val openHan: Int,
    val isYakuman: Boolean = false,
) {
    // ---- 1-han ----
    RIICHI           ("立直",   "Riichi",            1, -1),
    DOUBLE_RIICHI    ("ダブル立直", "Double Riichi",  2, -1),
    IPPATSU          ("一発",   "Ippatsu",            1, -1),
    MENZEN_TSUMO     ("門前清自摸和", "Menzen Tsumo",  1, -1),
    TANYAO           ("断么九", "Tanyao",             1,  1),
    PINFU            ("平和",   "Pinfu",              1, -1),
    IIPEIKO          ("一盃口", "Iipeiko",            1, -1),
    HAITEI_RAOYUE    ("海底摸月", "Haitei",           1,  1),
    HOUTEI_RAOYUI    ("河底撈魚", "Houtei",           1,  1),
    RINSHAN_KAIHOU   ("嶺上開花", "Rinshan",          1,  1),
    CHANKAN          ("槍槓",   "Chankan",            1,  1),
    YAKUHAI          ("役牌",   "Yakuhai",            1,  1),

    // ---- 2-han ----
    CHIITOITSU       ("七対子", "Chiitoitsu",         2, -1),
    SANSHOKU_DOUJUN  ("三色同順", "Sanshoku Doujun",  2,  1),
    ITTSU            ("一気通貫", "Ittsu",             2,  1),
    TOITOI           ("対々和", "Toitoi",              2,  2),
    SANANKOU         ("三暗刻", "Sanankou",            2,  2),
    SANKANTSU        ("三槓子", "Sankantsu",           2,  2),
    SHOUSANGEN       ("小三元", "Shousangen",          2,  2),
    HONROUTOU        ("混老頭", "Honroutou",           2,  2),
    CHANTA           ("混全帯么九", "Chanta",          2,  1),
    SANSHOKU_DOUKOU  ("三色同刻", "Sanshoku Doukou",   2,  2),

    // ---- 3-han ----
    RYANPEIKO        ("二盃口", "Ryanpeiko",           3, -1),
    HONITSU          ("混一色", "Honitsu",             3,  2),
    JUNCHAN          ("純全帯么九", "Junchan",         3,  2),

    // ---- 6-han ----
    CHINITSU         ("清一色", "Chinitsu",            6,  5),

    // ---- Yakuman ----
    KOKUSHI_MUSOU    ("国士無双", "Kokushi Musou",     YAKUMAN_HAN, -1, true),
    SUUANKOU         ("四暗刻",  "Suuankou",           YAKUMAN_HAN, -1, true),
    DAISANGEN        ("大三元",  "Daisangen",          YAKUMAN_HAN, YAKUMAN_HAN, true),
    SHOSUUSHI        ("小四喜",  "Shosuushi",          YAKUMAN_HAN, YAKUMAN_HAN, true),
    DAISUUSHI        ("大四喜",  "Daisuushi",          YAKUMAN_HAN, YAKUMAN_HAN, true),
    TSUUIISOU        ("字一色",  "Tsuuiisou",          YAKUMAN_HAN, YAKUMAN_HAN, true),
    CHINROUTOU       ("清老頭",  "Chinroutou",         YAKUMAN_HAN, YAKUMAN_HAN, true),
    RYUUIISOU        ("緑一色",  "Ryuuiisou",          YAKUMAN_HAN, YAKUMAN_HAN, true),
    CHUUREN_POUTOU   ("九蓮宝燈", "Chuuren Poutou",   YAKUMAN_HAN, -1, true),
    SUUKANTSU        ("四槓子",  "Suukantsu",          YAKUMAN_HAN, YAKUMAN_HAN, true),
    TENHOU           ("天和",    "Tenhou",             YAKUMAN_HAN, -1, true),
    CHIIHOU          ("地和",    "Chiihou",            YAKUMAN_HAN, -1, true),
    ;

    companion object {
        const val YAKUMAN_HAN = 13
    }
}

data class YakuResult(
    val yaku: Yaku,
    val han: Int,
    val appliedAsOpen: Boolean,
)
