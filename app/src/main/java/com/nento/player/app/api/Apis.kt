package com.nento.player.app.api

import com.nento.player.app.responses.UpdateResponse
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.*

interface  Apis {

  /*  @PUT("api/players/register-info")
    fun registerInfo(@Body infoBodyJson:String, @Header("content-type") contentType: String)
        : Call<String>

    @GET("api/player-and-contents")
    fun getPlayersContent(@Query("screen_no") screenNo: Int) : Call<ContentResponse>  */

    @POST("api/checkAppUpdate")
    fun checkForUpdate(@Body versionInfoJson: String,
                       @Header("Content-Type") contentType: String) : Call<UpdateResponse>

    @Multipart
    @PUT("api/player-snap-short")
    fun sendScreenShot(@Part("screenNumber") screenNumber: String,
                       @Part ssImage: MultipartBody.Part) : Call<String>

    @PUT("api/players/heartbeat")
    fun sendHeartBeat(@Body heartBeat: String, @Header("content-type") contentType: String) : Call<String>

    @POST("api/player-screen-update")
    fun sendResetCommandToServer(@Body resetJson: String,  @Header("content-type") contentType: String) : Call<String>
}