package com.example.carparkingsmart.api

data class RegisterResponse(val id: Int, val username: String, val email: String)
data class LoginResponse(val token: String)
data class StationResponse(
    val id: Int,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val total_slots: Int,
    val available_slots: Int
)
data class BookingResponse(val id: Int, val status: String, val qr_code_data: String, val expiry_time: String)