package com.nento.player.app.responses

import com.google.gson.annotations.SerializedName

class UpdateResponse {

    @SerializedName("data")
    var data: Udata? = null

    class Udata {

        @SerializedName("updateApp")
        var isUpdateAvailable: Boolean = false

        @SerializedName("forceUpdate")
        var forceUpdate: Int? = null

        @SerializedName("updateURL")
        var updateUrl : String? = null

        @SerializedName("newVersion")
        var newVersion : Int? = null
    }
}