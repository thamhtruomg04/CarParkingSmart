package com.example.carparkingsmart
data class DirectionStep(
    val instruction: String,
    val distance: String,
    val duration: String,
    val maneuver: String,
    val roadName: String = "",
    val startLat: Double = 0.0,
    val startLon: Double = 0.0,
    val endLat: Double = 0.0,
    val endLon: Double = 0.0
) {
    // ✅ Chuyển vào trong class, không để top-level nữa
    fun maneuverIcon(): String {
        return when {
            maneuver.contains("depart")       -> "🏁"
            maneuver.contains("arrive")       -> "📍"
            maneuver.contains("turn-left")    -> "⬅️"
            maneuver.contains("sharp-left")   -> "↰"
            maneuver.contains("slight-left")  -> "↖️"
            maneuver.contains("turn-right")   -> "➡️"
            maneuver.contains("sharp-right")  -> "↱"
            maneuver.contains("slight-right") -> "↗️"
            maneuver.contains("u-turn")       -> "🔄"
            maneuver.contains("roundabout")   -> "🔵"
            maneuver.contains("merge")        -> "🔀"
            maneuver.contains("fork")         -> "⑂"
            maneuver.contains("left")         -> "⬅️"
            maneuver.contains("right")        -> "➡️"
            else                              -> "⬆️"
        }
    }

    fun hudColor(): String {
        return when {
            maneuver.contains("arrive")                        -> "#2E7D32"
            maneuver.contains("u-turn")                        -> "#B71C1C"
            maneuver.contains("left") || maneuver.contains("right") -> "#1565C0"
            maneuver.contains("roundabout")                    -> "#6A1B9A"
            else                                               -> "#37474F"
        }
    }
}