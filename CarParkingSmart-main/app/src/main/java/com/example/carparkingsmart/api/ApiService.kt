package com.example.carparkingsmart.api

import okhttp3.ResponseBody
import retrofit2.Response // Để dùng Response<T>
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Path // QUAN TRỌNG: Để truyền ID vào URL

interface ApiService {
    @POST("api/register/")
    suspend fun register(@Body user: RegisterRequest): RegisterResponse

    @POST("api/api-token-auth/")
    suspend fun login(@Body credentials: LoginRequest): LoginResponse

    @GET("api/stations/")
    suspend fun getStations(): List<StationResponse>

    @FormUrlEncoded
    @POST("api/bookings/")
    suspend fun createBooking(
        @Field("user_id") userId: String,
        @Field("station") stationId: Int,
        @Field("status") status: String
    ): Response<BookingResponse>

    @FormUrlEncoded
    @POST("api/bookings/{id}/update_status/")
    suspend fun updateBookingStatus(
        @Path("id") bookingId: Int, // Map {id} trên URL với biến này
        @Field("status") status: String
    ): Response<ResponseBody>


    // Thêm class này vào project của bạn
    data class BookingResponse(
        val id: Int,
        val user_id: String,
        val station: Int,
        val status: String,
        val booking_time: String,
        val expiry_time: String? = null
    )


}