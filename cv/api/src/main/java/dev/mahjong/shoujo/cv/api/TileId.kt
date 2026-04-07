package dev.mahjong.shoujo.cv.api

/**
 * Canonical, model-agnostic identifier for all 34 standard Riichi Mahjong tile types.
 *
 * This enum is the stable currency exchanged between the CV pipeline and the rest of the app.
 * It MUST NOT change shape when the underlying model is replaced. If the new model uses
 * different internal class indices, the adapter is responsible for mapping to these values.
 *
 * Naming convention: {SUIT}_{NUMBER} for number tiles, {NAME} for honors.
 */
enum class TileId {
    // Man (Characters / 萬子)
    MAN_1, MAN_2, MAN_3, MAN_4, MAN_5, MAN_6, MAN_7, MAN_8, MAN_9,
    // Pin (Circles / 筒子)
    PIN_1, PIN_2, PIN_3, PIN_4, PIN_5, PIN_6, PIN_7, PIN_8, PIN_9,
    // Sou (Bamboo / 索子)
    SOU_1, SOU_2, SOU_3, SOU_4, SOU_5, SOU_6, SOU_7, SOU_8, SOU_9,
    // Winds (風牌)
    WIND_EAST, WIND_SOUTH, WIND_WEST, WIND_NORTH,
    // Dragons (三元牌)
    DRAGON_HAKU, DRAGON_HATSU, DRAGON_CHUN,
    // Sentinel for unrecognized / below-threshold detections
    UNKNOWN;

    val isNumberTile: Boolean get() = ordinal < 27
    val isHonorTile:  Boolean get() = ordinal in 27..33
    val isUnknown:    Boolean get() = this == UNKNOWN
}
