data class StationResponse(
    val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("ward") val ward: String?, // Thêm dấu ?
    @SerializedName("address") val address: String?, // Thêm dấu ?
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("total_slots") val total_slots: Int?, // Thêm dấu ?
    @SerializedName("available_slots") val available_slots: Int? // Thêm dấu ?
)