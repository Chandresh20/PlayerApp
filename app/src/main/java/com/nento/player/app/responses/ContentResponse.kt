package com.nento.player.app.responses

import com.google.gson.annotations.SerializedName

class ContentResponse {

    @SerializedName("_id")
    var id: String? = null

    @SerializedName("playerName")
    var playerName: String? = null

    @SerializedName("pushPlayerId")
    var pushPlayerId: String? = null

    @SerializedName("contentType")
    var contentType: String? = null

    @SerializedName("content")
    var content : Content? = null

    @SerializedName("items")
    var items: List<ContentItem>? = null


    class Content {

        @SerializedName("_id")
        var id: String? = null

        @SerializedName("orientation")
        var orientation: String? = null

        @SerializedName("thumbnail")
        var thumbnail: String? = null
    }

    class ContentItem {

        @SerializedName("_id")
        var id: String? = null

        @SerializedName("mediaId")
        var mediaID: String? = null

        @SerializedName("contentType")
        var contentType : String? = null

        @SerializedName("duration")
        var duration: Int? = null

        @SerializedName("playlistId")
        var playlistId: String? = null

        @SerializedName("original_file_name")
        var originalFileName : String? = null

        @SerializedName("mediaType")
        var mediaType: String? = null
    }
}