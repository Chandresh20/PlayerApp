package com.nento.player.app

import android.content.Context
import android.util.Log
import android.widget.VideoView

class CustomVideoView(ctx: Context) : VideoView(ctx) {

    private var mVideoWidth = 0
    private var mVideoHeight = 0

    /* fun setVideoSize(width: Int, height: Int) {
         mVideoWidth = width
         mVideoHeight = height
     }  */

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var width = getDefaultSize(mVideoWidth, widthMeasureSpec)
        var height = getDefaultSize(mVideoHeight, heightMeasureSpec)
        if (mVideoWidth > 0 && mVideoHeight > 0) {
            if (mVideoWidth * height > width * mVideoHeight) {
                height = width * mVideoHeight / mVideoWidth
            } else if (mVideoWidth * height < width * mVideoHeight) {
                width = height * mVideoWidth / mVideoHeight
            } else {
                Log.d("Video", "Parameters are 0")
            }
        }
        setMeasuredDimension(width, height)
    }
}