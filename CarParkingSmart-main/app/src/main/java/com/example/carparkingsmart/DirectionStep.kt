package com.example.carparkingsmart

/**
 * Model cho mỗi bước chỉ đường.
 * Thêm trường roadName so với phiên bản cũ.
 */
data class DirectionStep(
    val instruction: String,
    val distance: String,
    val duration: String,
    val maneuver: String,
    val roadName: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double
)

/**
 * Icon tương ứng với từng loại maneuver để hiển thị trên HUD
 */
fun DirectionStep.maneuverIcon(): String {
    return when {
        maneuver.contains("depart")         -> "🏁"
        maneuver.contains("arrive")         -> "📍"
        maneuver.contains("turn-left")      -> "⬅️"
        maneuver.contains("sharp-left")     -> "↰"
        maneuver.contains("slight-left")    -> "↖️"
        maneuver.contains("turn-right")     -> "➡️"
        maneuver.contains("sharp-right")    -> "↱"
        maneuver.contains("slight-right")   -> "↗️"
        maneuver.contains("u-turn")         -> "🔄"
        maneuver.contains("roundabout")     -> "🔵"
        maneuver.contains("merge")          -> "🔀"
        maneuver.contains("fork")           -> "⑂"
        else                                -> "⬆️"
    }
}

/**
 * Màu nền của HUD theo loại maneuver
 */
fun DirectionStep.hudColor(): String {
    return when {
        maneuver.contains("arrive")         -> "#2E7D32"  // xanh lá đậm
        maneuver.contains("u-turn")         -> "#B71C1C"  // đỏ đậm
        maneuver.contains("left") ||
                maneuver.contains("right")          -> "#1565C0"  // xanh dương
        maneuver.contains("roundabout")     -> "#6A1B9A"  // tím
        else                                -> "#37474F"  // xám tối
    }
}