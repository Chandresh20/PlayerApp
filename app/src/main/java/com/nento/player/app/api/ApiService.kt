package com.nento.player.app.api

import com.nento.player.app.Constants
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class ApiService {

    companion object {
        private val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(Constants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(Constants.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(Constants.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        private val retrofitBuilder = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(client)
            .addConverterFactory(retrofit2.converter.scalars.ScalarsConverterFactory.create())
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())

        private val retrofit = retrofitBuilder.build()
        val apiService : Apis = retrofit.create(Apis::class.java)
    }
}