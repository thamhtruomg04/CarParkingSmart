package com.example.carparkingsmart.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 1. Thay thế bằng URL của Render
    private const val BASE_URL = "https://carparkingsmart.onrender.com/"

    // 2. Dùng client mặc định (vì HTTPS của Render đã chuẩn rồi)
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