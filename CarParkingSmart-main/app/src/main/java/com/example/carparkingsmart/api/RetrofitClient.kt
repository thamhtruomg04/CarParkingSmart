package com.example.carparkingsmart.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Nếu dùng máy ảo Android, hãy dùng 10.0.2.2.
    // Nếu dùng máy thật, hãy dùng địa chỉ IP của máy tính (ví dụ 192.168.1.x)
    private const val BASE_URL = "http://192.168.1.11:8000/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}