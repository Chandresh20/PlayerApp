package com.nento.player.app

import com.google.gson.annotations.SerializedName
import java.io.Serializable

class PlaylistObject : Serializable {

    @SerializedName("playlist")
    var playlist : List<PlaylistItem>? = null

    @SerializedName("is_schedule")
    var scheduledOrNot : Boolean? = null

    @SerializedName("type")
    var type: String? = null

    class PlaylistItem : Serializable {

        @SerializedName("_id")
        var id: String? = null

        @SerializedName("mediaId")
        var mediaId: String? = null

        @SerializedName("contentType")
        var contentType : String? = null

        @SerializedName("playlistId")
        var playListId: String? = null

        @SerializedName("duration")
        var duration : Int? = null

        @SerializedName("mediaName")
        var mediaName : String? = null

        @SerializedName("isIncluded")
        var sIncluded : Boolean? = null

        // added for layout inside playlist
        @SerializedName("shareData")
        var shareData : String? = null

        @SerializedName("imageUrl")
        var imageUrl : List<ImageUrlObj>? = null
    }

    class ImageUrlObj : Serializable {

        @SerializedName("name")
        var name : String? = null

        @SerializedName("url")
        var url : String? = null
    }
}