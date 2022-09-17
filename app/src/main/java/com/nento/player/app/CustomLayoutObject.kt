package com.nento.player.app

import com.google.gson.annotations.SerializedName

class CustomLayoutObject {

    @SerializedName("_id")
    var id: String? = null

    @SerializedName("isVertical")
    var isVertical : Boolean? = false

    @SerializedName("layout")
    var layout : List<LayoutInfo>? = null

    @SerializedName("userId")
    var userId: String? = null

    @SerializedName("imageUrl")
    var imageUrl: List<URLObject>? = null

    class LayoutInfo {

        @SerializedName("x")
        var x : Double? = null

        @SerializedName("y")
        var y : Double? = null

        @SerializedName("width")
        var width: Double? = null

        @SerializedName("height")
        var height : Double? = null

        @SerializedName("fill")
        var fill: String? = null

        /*     @SerializedName("id")
             var id : Int? = null  */

        @SerializedName("opacity")
        var opacity: Float? = null

        @SerializedName("media")
        var media : List<MediaInfo>? = null

        @SerializedName("repeat")
        var repeat : Boolean? = null
    }

    class MediaInfo {

        @SerializedName("original_file_name")
        var fileName : String? = null

        @SerializedName("type")
        var type: String? = null

        @SerializedName("time")
        var timeInSeconds: Int? = null
    }

    class URLObject {

        @SerializedName("name")
        var name: String? = null

        @SerializedName("url")
        var url: String? = null
    }
}