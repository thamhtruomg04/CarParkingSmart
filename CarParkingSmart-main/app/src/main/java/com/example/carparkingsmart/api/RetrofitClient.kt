// RetrofitClient.kt
"""package com.example.carparkingsmart.api
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://adrianne-jaggier-aiko.ngrok-free.dev/"

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("ngrok-skip-browser-warning", "true") // Dòng này cực kỳ quan trọng
            .build()
        chain.proceed(request)
    }.build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // Thêm client đã cấu hình vào đây
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}"""


package com.example.carparkingsmart.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("api/register/")
    suspend fun register(@Body user: RegisterRequest): RegisterResponse

    @POST("api/api-token-auth/")
    suspend fun login(@Body credentials: LoginRequest): LoginResponse

    @GET("api/stations/")
    suspend fun getStations(): List<StationResponse>

    @GET("api/stations/{id}/slots/")
    suspend fun getSlots(
        @Path("id") stationId: Int
    ): Response<List<com.example.carparkingsmart.ChargingSlot>>

    @FormUrlEncoded
    @POST("api/bookings/")
    suspend fun createBooking(
        @Field("user_id") userId: String,
        @Field("station") stationId: Int,
        @Field("slot") slotId: Int,
        @Field("status") status: String
    ): Response<BookingResponse>

    @FormUrlEncoded
    @POST("api/bookings/{id}/update_status/")
    suspend fun updateBookingStatus(
        @Path("id") bookingId: Int,
        @Field("status") status: String
    ): Response<ResponseBody>

    data class BookingResponse(
        val id: Int,
        val user_id: String,
        val station: Int,
        val slot: Int?,
        val status: String,
        val booking_time: String,
        val expiry_time: String? = null
    )
}