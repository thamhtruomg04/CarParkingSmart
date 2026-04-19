package com.example.carparkingsmart.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // Đăng ký tài khoản
    @POST("api/register/")
    suspend fun register(@Body user: RegisterRequest): RegisterResponse

    // Đăng nhập lấy Token
    @POST("api/api-token-auth/")
    suspend fun login(@Body credentials: LoginRequest): LoginResponse

    // Lấy danh sách trạm sạc
    @GET("api/stations/")
    suspend fun getStations(): List<StationResponse>

    // Lấy danh sách ô sạc theo ID trạm sạc
    @GET("api/stations/{id}/slots/")
    suspend fun getSlots(
        @Path("id") stationId: Int
    ): Response<List<com.example.carparkingsmart.ChargingSlot>>

    // Tạo đơn đặt chỗ mới
    @FormUrlEncoded
    @POST("api/bookings/")
    suspend fun createBooking(
        @Field("user_id") userId: String,
        @Field("station") stationId: Int,
        @Field("slot") slotId: Int,
        @Field("status") status: String
    ): Response<BookingResponse>

    // Cập nhật trạng thái đặt chỗ
    @FormUrlEncoded
    @POST("api/bookings/{id}/update_status/")
    suspend fun updateBookingStatus(
        @Path("id") bookingId: Int,
        @Field("status") status: String
    ): Response<ResponseBody>


    @GET("api/bookings/{id}/")
    suspend fun getBookingDetail(
        @Path("id") bookingId: Int
    ): Response<BookingResponse>

    // Sửa lại tên cho nhất quán — dùng 1 cái duy nhất
    @POST("api/bookings/{id}/confirm_payment/")
    suspend fun confirmBookingAndSubtractSlot(
        @Path("id") bookingId: Int
    ): Response<ResponseBody>

    // Cấu trúc dữ liệu phản hồi cho Đặt chỗ
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