package com.example.carparkingsmart.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

"""interface ApiService {
    @Headers("ngrok-skip-browser-warning: 69420")
    @POST("api/register/")
    suspend fun register(@Body user: RegisterRequest): RegisterResponse

    @Headers("ngrok-skip-browser-warning: 69420")
    @POST("api/api-token-auth/")
    suspend fun login(@Body credentials: LoginRequest): LoginResponse

    @Headers("ngrok-skip-browser-warning: 69420")
    @GET("api/stations/")
    suspend fun getStations(): List<StationResponse>

    // --- 1. THÊM HÀM LẤY DANH SÁCH Ô SẠC TỪ API ---
    @Headers("ngrok-skip-browser-warning: 69420")
    @GET("api/stations/{id}/slots/")
    suspend fun getSlots(
        @Path("id") stationId: Int
    ): Response<List<com.example.carparkingsmart.ChargingSlot>>

    // --- 2. HÀM ĐẶT CHỖ (Đã có slotId) ---
    @FormUrlEncoded
    @Headers("ngrok-skip-browser-warning: 69420")
    @POST("api/bookings/")
    suspend fun createBooking(
        @Field("user_id") userId: String,
        @Field("station") stationId: Int,
        @Field("slot") slotId: Int,
        @Field("status") status: String
    ): Response<BookingResponse>

    @FormUrlEncoded
    @Headers("ngrok-skip-browser-warning: 69420")
    @POST("api/bookings/{id}/update_status/")
    suspend fun updateBookingStatus(
        @Path("id") bookingId: Int,
        @Field("status") status: String
    ): Response<ResponseBody>

    // --- 3. CẬP NHẬT DATA CLASS ---
    data class BookingResponse(
        val id: Int,
        val user_id: String,
        val station: Int,
        val slot: Int?,
        val status: String,
        val booking_time: String,
        val expiry_time: String? = null
    )
}"""


object RetrofitClient {
    // 1. Thay link ngrok bằng link Render của bạn
    private const val BASE_URL = "https://carparkingsmart.onrender.com/"

    // 2. Không cần Interceptor cho ngrok nữa, dùng client mặc định cho nhanh
    private val client = OkHttpClient.Builder().build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}