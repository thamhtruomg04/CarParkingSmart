package com.example.carparkingsmart.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Header

interface ApiService {
    @POST("api/register/")
    suspend fun register(@Body user: RegisterRequest): RegisterResponse

    // SỬA DÒNG NÀY: Thêm "api/" vào phía trước
    @POST("api/api-token-auth/")
    suspend fun login(@Body credentials: LoginRequest): LoginResponse

    @GET("api/stations/")
    suspend fun getStations(): List<StationResponse>

    @POST("api/bookings/")
    suspend fun createBooking(
        @Header("Authorization") token: String,
        @Body booking: BookingRequest
    ): BookingResponse
}

