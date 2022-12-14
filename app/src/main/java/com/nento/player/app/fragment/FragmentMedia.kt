package com.nento.player.app.fragment

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.*
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nento.player.app.*
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.*
import java.io.File
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.util.*
import kotlin.Exception
import kotlin.collections.ArrayList


const val CURRENT_TEMPLATE = 0
const val CURRENT_PLAYLIST = 1
const val CURRENT_CUSTOM = 2

class FragmentMedia : Fragment(), TextureView.SurfaceTextureListener {

    private lateinit var videoHandler: Handler
    private lateinit var webViewHandler : Handler
    private lateinit var customLayoutHandler: Handler
    private lateinit var errorHandler : Handler
    private lateinit var ctx: Context
    private var imRunning = false
    private var currentMedia = -1
    private val itemArray = ArrayList<DisplayItems>()
    private val mediaPlayerList = ArrayList<MediaPlayer>()
    private val youtubePlayerList = ArrayList<YouTubePlayerView>() // list of youtube player that needs to be release at end
    private lateinit var mediaPlayer : MediaPlayer
    private var totalCustomContent = 0
    private var customContentFinished = 0
    private var areClocksRunning = false

    private lateinit var glideHandler: Handler
    private lateinit var timeHandler : Handler
    private lateinit var timeRunnable: Runnable
    private val allClocks = ArrayList<ClockObject>()
    private val wMulti: Float = (MainActivity.displayWidth.toFloat() / Constants.CUSTOM_WIDTH_THEIR)
    private val hMulti: Float = (MainActivity.displayHeight.toFloat() / Constants.CUSTOM_HEIGHT_THEIR)

    private var fontSizeMultiplier : Float = 1f

    private lateinit var pImage : ImageView
    private lateinit var pImage2 : ImageView
    private lateinit var pVideo : TextureView
    private lateinit var customLayout : ConstraintLayout
    private lateinit var proBar : ProgressBar

    @SuppressLint("SetJavaScriptEnabled", "CheckResult")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ctx = findNavController().context
        Constants.onSplashScreen = false
        fontSizeMultiplier = (MainActivity.displayHeight.toFloat() / resources.displayMetrics.densityDpi) / 3.375f
        val rootView = inflater.inflate(R.layout.fragment_media, container, false)
        pImage = rootView.findViewById(R.id.previewImage)
        pImage2 = rootView.findViewById(R.id.previewImage2)
        pVideo = rootView.findViewById(R.id.textureVideo)
        val weatherAndTimeLayout = rootView.findViewById<ConstraintLayout>(R.id.weatherAndTimeLayout)
        mediaPlayer = MediaPlayer()
        var isEvenImage = false
        customLayout = rootView.findViewById(R.id.customLayout)
        playlistTextureView = pVideo
        playlistTextureView?.surfaceTextureListener = this
        val screenId = rootView.findViewById<TextView>(R.id.screenIdText)
        proBar = rootView.findViewById(R.id.mediaProgressBar)
        screenId.text = Constants.screenID

        timeRunnable = object : Runnable {
            val cal = Calendar.getInstance()
            override fun run() {
                if(!areClocksRunning) {
                    return
                }
                for(clock in allClocks) {
                    cal.timeInMillis = clock.time
                    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
                    var hour12 = cal.get(Calendar.HOUR)
                    if (hour12 == 0) {
                        hour12 = 12
                    }
                    val amPm = cal.get(Calendar.AM_PM)
                    val min = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
                   /* if (min.length == 1) {
                        min = "0$min"
                    } */

                    val clockText : String  = if(clock.isTimeElseDate) {
                        var amPmS: String
                        if(!clock.is24Hours) {
                            val hrStr = hour12.toString().padStart(2, '0')
                        /*    if(hrStr.length == 1) {
                                hrStr = "0$hrStr"
                            } */
                            amPmS = "AM"
                            if(amPm == 1) {
                                amPmS = "PM"
                            }
                            "$hrStr:$min $amPmS"
                         } else {
                             val hrStr = hour24.toString().padStart(2, '0')
                          /*  if(hrStr.length == 1) {
                                hrStr = "0$hrStr"
                            } */
                            "$hrStr:$min"
                        }
                    } else {
                        val dayName = cal.get(Calendar.DAY_OF_WEEK)
                        val month = cal.get(Calendar.MONTH)
                        val date = cal.get(Calendar.DATE)
                        val year = cal.get(Calendar.YEAR)
                        "${Constants.getDayNameFromCal(dayName)}, ${Constants.getMonthNameFromCal(month)} $date, $year"
                    }
                    clock.textView.text = clockText
                    clock.textView.setBackgroundColor(clock.bgColor)
                    clock.time += 30000
                }
                timeHandler.postDelayed(this, 30000)
            }
        }
        timeHandler = Handler(Looper.getMainLooper())
        errorHandler = Handler(Looper.getMainLooper()) {
        //    val msg = it.obj as String
       //     Log.e("MediaError", msg)
            true
        }

        glideHandler = Handler(Looper.getMainLooper()) {
            // message format ArrayOf(file: File, isVertical: Boolean)
            val info = it.obj as Array<*>
            val file = info[0] as File
            val isVertical = info[1] as Boolean
            playType = PLAY_TYPE_IMAGE
            pVideo.visibility = View.GONE
            customLayout.visibility = View.GONE
            proBar.visibility = View.GONE
            releaseYoutubePlayers()
            customLayout.removeAllViews()
            if ((Constants.rotationAngle == 0f
                        || Constants.rotationAngle == 180f)
                && !isVertical) {
                if (isEvenImage) {
                    pImage2.scaleType = ImageView.ScaleType.FIT_XY
                } else {
                    pImage.scaleType = ImageView.ScaleType.FIT_XY
                }
            }
            if ((Constants.rotationAngle == 0f
                        || Constants.rotationAngle == 180f)
                && isVertical) {
                if (isEvenImage) {
                    pImage2.scaleType = ImageView.ScaleType.FIT_CENTER
                } else {
                    pImage.scaleType = ImageView.ScaleType.FIT_CENTER
                }
            }
            if ((Constants.rotationAngle == 90f
                        || Constants.rotationAngle == 270f)
                && isVertical) {
                if (isEvenImage) {
                    pImage2.scaleType =ImageView.ScaleType.FIT_XY
                } else {
                    pImage.scaleType = ImageView.ScaleType.FIT_XY
                }
            }

            if ((Constants.rotationAngle == 90f
                        || Constants.rotationAngle == 270f)
                && !isVertical) {
                if (isEvenImage) {
                    pImage2.scaleType = ImageView.ScaleType.FIT_CENTER
                } else {
                    pImage.scaleType = ImageView.ScaleType.FIT_CENTER
                }
            }
            val glide = Glide.with(ctx)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
            if (Constants.rotationAngle > 0f) {
                glide.override(MainActivity.displayHeight, MainActivity.displayWidth)
                glide.transform(RotateTransformation(Constants.rotationAngle))
            } else {
                glide.override(MainActivity.displayWidth, MainActivity.displayHeight)
            }
            glide.override(MainActivity.displayWidth, MainActivity.displayHeight)
            if (isEvenImage) {
                glide.into(pImage2)
                isEvenImage = false
                CoroutineScope(Dispatchers.Main).launch {
                    delay(500)
                    pImage.visibility = View.GONE
                    pImage2.visibility = View.VISIBLE
                }
            } else {
                glide.into(pImage)
                isEvenImage = true
                CoroutineScope(Dispatchers.Main).launch {
                    delay(500)
                    pImage.visibility = View.VISIBLE
                    pImage2.visibility = View.GONE
                }
            }
            // show time and weather if set true
            setWeatherAndTimeLayout(weatherAndTimeLayout)

            if (mediaPlayer.isPlaying) {
                try {
                    mediaPlayer.stop()
                } catch (e:Exception) {
                }
            }
            true
        }
        videoHandler = Handler(Looper.getMainLooper()) {
            val file = it.obj as String
            if (file == Constants.STOP_VIDEO) {
                try {
                    mediaPlayer.stop()
                } catch (e:Exception) {
                }
                return@Handler false
            }
            playOnMediaPlayer(file)
            playType = PLAY_TYPE_VIDEO
            glideHandler.postDelayed( {
                pImage.visibility = View.GONE
                pImage2.visibility = View.GONE
                pVideo.visibility = View.VISIBLE
                customLayout.visibility = View.GONE
                proBar.visibility = View.GONE
                releaseYoutubePlayers()
                customLayout.removeAllViews()
            } ,2000)  // video play needs some time directly showing video/textureView
            // make the app frozen for few moment. that's why it delayed
            setWeatherAndTimeLayout(weatherAndTimeLayout)
            true
        }

        customLayoutHandler = Handler(Looper.getMainLooper()) {
            playType = PLAY_TYPE_LAYOUT
            pImage.visibility = View.GONE
            pImage2.visibility = View.GONE
            pVideo.visibility = View.GONE
            customLayout.visibility = View.VISIBLE
            releaseYoutubePlayers()
            customLayout.removeAllViews()
            proBar.visibility = View.GONE
            if (mediaPlayer.isPlaying) {
                try {
                    mediaPlayer.stop()
                } catch (e:Exception) {
                }
            }
            try {
                // show time and weather if set true
                setWeatherAndTimeLayout(weatherAndTimeLayout)
                //convert jsonData to Object
                val gson = Gson()
                val typeT = object : TypeToken<CustomLayoutObject>() { }
                val customInfoFile = File(MainActivity.storageDir, Constants.CUSTOM_LAYOUT_JSON_NAME)
                val jsonStr = customInfoFile.readText()
                val dLayoutObject = gson.fromJson<CustomLayoutObject>(jsonStr, typeT.type)

                // get total numbers of youtube videos, if more than one video then youtube should be muted,
                // to play multiple videos simultaneously
                var youtubeVideoCount = 0
                for(layout in dLayoutObject.layout ?: emptyList()) {
                    for(mediaInfo in layout.media ?: emptyList()) {
                        if(mediaInfo.type == Constants.MEDIA_YOUTUBE) {
                            youtubeVideoCount++
                        }
                    }
                }

                imRunning = true

                totalCustomContent = dLayoutObject.layout?.size ?: 0
                customContentFinished = 0
                if (dLayoutObject.isVertical == true) {
                    if (Constants.rotationAngle == 0f) {
                        Constants.rotationAngle = 90f
                    } else if (Constants.rotationAngle == 180f) {
                        Constants.rotationAngle = 270f
                    }
                    Constants.lastLayoutVertical = true
                } else {
                    if (Constants.rotationAngle == 90f) {
                        if(Constants.lastLayoutVertical) {
                            Constants.rotationAngle = 0f
                        } else {
                            Constants.rotationAngle = 180f
                        }
                    } else if (Constants.rotationAngle == 270f) {
                        if(Constants.lastLayoutVertical) {
                            Constants.rotationAngle = 180f
                        } else {
                            Constants.rotationAngle = 0f
                        }
                    }
                    Constants.lastLayoutVertical = false
                }
                MainActivity.sharedPreferences.edit().putFloat(
                    Constants.PREFS_ROTATION_ANGLE, Constants.rotationAngle).apply()
                for (layout in (dLayoutObject.layout ?: emptyList())) {
                    var layoutWidth : Int = (layout.width ?: 0).toInt()
                    var layoutHeight : Int = (layout.height ?: 0).toInt()
                    var layoutX : Int = (layout.x ?: 0).toInt()
                    var layoutY : Int = (layout.y ?: 0).toInt()

                    if (dLayoutObject.isVertical == true) {
                        layoutWidth = (layout.height ?: 0).toInt()
                        layoutHeight = (layout.width ?: 0).toInt()
                        layoutX = (layout.y ?: 0).toInt()
                        layoutY = (layout.x ?: 0).toInt()
                    }
                    val linearLayout = LinearLayout(ctx)
                    val isVertical : Boolean = dLayoutObject.isVertical ?: false
                    if (layout.opacity != null) {
                        linearLayout.alpha = layout.opacity ?: 1f
                    }
                    linearLayout.layoutParams = ConstraintLayout.LayoutParams(
                        (layoutWidth * wMulti).toInt(), (layoutHeight * hMulti).toInt()).apply {
                        when(Constants.rotationAngle) {
                            0f -> {
                                if (isVertical) {
                                    // not available
                                } else {
                                    startToStart = R.id.customLayout
                                    topToTop = R.id.customLayout
                                    marginStart = (layoutX * wMulti).toInt()
                                    topMargin = (layoutY * hMulti).toInt()
                                }
                            }
                            90f -> {
                                if (isVertical) {
                                    endToEnd = R.id.customLayout
                                    topToTop = R.id.customLayout
                                    marginEnd = (layoutX * wMulti).toInt()
                                    topMargin = (layoutY * hMulti).toInt()
                                } else {
                                    // not supported
                                }
                            }
                            180f -> {
                                if (isVertical) {
                                    // not supported
                                } else {
                                    endToEnd = R.id.customLayout
                                    bottomToBottom = R.id.customLayout
                                    marginEnd = (layoutX * wMulti).toInt()
                                    bottomMargin = (layoutY * hMulti).toInt()
                                }
                            }
                            270f -> {
                                if (isVertical) {
                                    startToStart = R.id.customLayout
                                    topToTop = R.id.customLayout

                                    marginStart = (layoutX * wMulti).toInt()
                                    topMargin =
                                        MainActivity.displayHeight - (layoutHeight * hMulti).toInt() - (layoutY * hMulti).toInt()
                                } else {
                                    // not supported
                                }
                            }
                        }
                    }
                    customLayout.addView(linearLayout)
      //              customWebView = null
      //              youView?.release()
       //             youView = null
                    CoroutineScope(Dispatchers.Main).launch {
                        imRunning = true
                        // with new method assuming looping to be always true
                        playCustomMedia2Async(
                            linearLayout,
                            layout.media ?: emptyList(),
                            dLayoutObject.isVertical ?: false,
                            youtubeVideoCount).await()
                        customContentFinished += 1
                    }
                }
            } catch (e: Exception) {
                errorHandler.obtainMessage(0, "Error: $e").sendToTarget()
            }
            true
        }
        val updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                currentMedia = -1
                areClocksRunning = false
                CoroutineScope(Dispatchers.Main).launch {
                     proBar.visibility = View.VISIBLE
                    while (imRunning) {
                        if (totalCustomContent > 0 && (totalCustomContent == customContentFinished)) {
                            imRunning = false
                        }
                        delay(500)
                    }
                    clearMediaPlayers()
                    when(p1?.action) {
                        Constants.NEW_TEMPLATE_READY_BROADCAST -> {
                            currentMedia = CURRENT_TEMPLATE
                            totalCustomContent = 0
                            val tempFile = File(MainActivity.storageDir, Constants.TEMPLATE_NAME)
                            val bMap= BitmapFactory.decodeFile(tempFile.toString())
                            if (bMap != null) {
                                val isVertical= bMap.height > bMap.width
                                glideHandler.obtainMessage(0, arrayOf(tempFile, isVertical)).sendToTarget()
                            }
                        }
                        Constants.NEW_PLAYLIST_READY_BROADCAST -> {
                            currentMedia = CURRENT_PLAYLIST
                            totalCustomContent = 0
//                            val itemsInPlaylist = p1.getStringArrayExtra(Constants.C_PLAYLIST)
//                            for (item in itemsInPlaylist ?: emptyArray()) { }
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(1000)
                                playPlaylistAsync().await()
                            }
                        }
                        Constants.NEW_WEB_VIEW_READY_BROADCAST -> {
                            val webViewStr = File(MainActivity.storageDir, "webView/index.html").toString()
                            webViewHandler.obtainMessage(0, webViewStr).sendToTarget()
                        }
                        Constants.NEW_CUSTOM_LAYOUT_READY_BROADCAST -> {
                            currentMedia = CURRENT_CUSTOM
                            customLayoutHandler.postDelayed( {
                                customLayoutHandler.obtainMessage(0,0).sendToTarget()
                            }, 2000)
                        }
                        else -> {
                            generateDisplayItemList()
                        }
                    }
                }
            }
        }
        ctx.registerReceiver(updateReceiver, IntentFilter(Constants.NEW_CONTENT_READY_BROADCAST))
        ctx.registerReceiver(updateReceiver, IntentFilter(Constants.NEW_PLAYLIST_READY_BROADCAST))
        ctx.registerReceiver(updateReceiver, IntentFilter(Constants.NEW_TEMPLATE_READY_BROADCAST))
        ctx.registerReceiver(updateReceiver, IntentFilter(Constants.NEW_CUSTOM_LAYOUT_READY_BROADCAST))
        MainActivity.mainViewModel.isIdHidden.observe(viewLifecycleOwner) { isHidden ->
            if (isHidden && (MainActivity.mainViewModel.isOffline.value == false)) {
                screenId.visibility = View.GONE
            } else {
                screenId.visibility = View.VISIBLE
            }
        }
        return rootView
    }

    override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
        val surface = Surface(p0)
        mediaPlayer.setSurface(surface)
    }

    private fun playOnMediaPlayer(file: String) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(file)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener {
                it.start()
            }
            mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
                if (Constants.rotationAngle == 90f || Constants.rotationAngle == 270f) {
                    playVideoVertically(width, height, playlistTextureView)
                } else {
                    playVideoNormally(width, height, playlistTextureView)
                }
            }
            mediaPlayer.setOnCompletionListener {
                mediaPlayer.stop()
            }
        } catch (e: Exception) {
        }

    }

    override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
    }

    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
        return false
    }

    override fun onSurfaceTextureUpdated(p0: SurfaceTexture) { }

    private fun playVideoNormally(videoWidth: Int, videoHeight: Int, textureView: TextureView?) {
        val screenWidth = MainActivity.displayWidth
        val screenHeight = MainActivity.displayHeight
        val vidAspectRatio = videoWidth.toFloat() / videoHeight
        val vidParams = if (videoWidth >= videoHeight) {
            val videoHeightOnScreen = screenWidth / vidAspectRatio
            ConstraintLayout.LayoutParams(screenWidth, videoHeightOnScreen.toInt()).apply {
                startToStart = R.id.fragmentMediaLayoutRoot
                topToTop = R.id.fragmentMediaLayoutRoot
                topMargin = ((screenHeight- videoHeightOnScreen) / 2).toInt()
            }
        } else {
            val videoWidthOnScreen = screenHeight * vidAspectRatio
            ConstraintLayout.LayoutParams(videoWidthOnScreen.toInt(), screenHeight).apply {
                startToStart = R.id.fragmentMediaLayoutRoot
                topToTop = R.id.fragmentMediaLayoutRoot
                marginStart = ((screenWidth - videoWidthOnScreen) / 2).toInt()
            }
        }
        textureView?.layoutParams = vidParams
        textureView?.rotation = Constants.rotationAngle
    }

    private fun playVideoVertically(videoWidth: Int, videoHeight: Int, textureView: TextureView?) {
        val screenWidth = MainActivity.displayWidth
        val screenHeight = MainActivity.displayHeight
        val vidAspectRatio = videoWidth.toFloat() / videoHeight
        val vidParams = if (videoHeight > videoWidth) {
            val newVideoWidth = screenWidth * vidAspectRatio
            ConstraintLayout.LayoutParams(newVideoWidth.toInt(), screenWidth).apply {
                startToStart = R.id.fragmentMediaLayoutRoot
                topToTop = R.id.fragmentMediaLayoutRoot
                topMargin = ((screenHeight - screenWidth) / 2)
                marginStart = ((screenWidth - newVideoWidth) / 2).toInt()
            }
        } else {
            val newVideoHeight = screenHeight / vidAspectRatio
            ConstraintLayout.LayoutParams(screenHeight, newVideoHeight.toInt()).apply {
                startToStart = R.id.fragmentMediaLayoutRoot
                topToTop = R.id.fragmentMediaLayoutRoot
                topMargin = ((screenHeight - newVideoHeight) / 2).toInt()
                marginStart = ((screenWidth - screenHeight) / 2)
            }
        }
        textureView?.layoutParams = vidParams
        textureView?.rotation = Constants.rotationAngle
    }

    private fun generateDisplayItemList() {
        itemArray.clear()
        val allRecords = RecordsAsJSON.getAllRecords()
        val records = allRecords.keys()
        for (key in records) {
            val pFile = File(MainActivity.storageDir, key)
            if (pFile.exists()) {
                itemArray.add(DisplayItems(key))
                //   , allRecords.get(key) as Int))
            }
        }
    }

    private suspend fun playPlaylistAsync() =
        coroutineScope {
            async(Dispatchers.IO) {
                val playlist = MainActivity.currentPlaylist.playlist
                main@ while (currentMedia == CURRENT_PLAYLIST) {
                    imRunning = true
                    inner@ for (item in playlist ?: emptyList()) {
                        if (item.sIncluded ==  false) {
                            continue@inner
                        }
                        if((item.shareData?.length ?: 0) > 10) {
                     //       Log.d("PlaylistLayoutShareData", item.shareData.toString())
                            playLayoutInMediaAsync(item.shareData ?: "[]").await()
                            for (i in 0 until (item.duration ?: 20)) {
                                if (currentMedia != CURRENT_PLAYLIST) {
                                    break@inner
                                }
                                delay(1000)
                            }
                            clearMediaPlayers()
                            continue@inner
                        }
                        val itemName : String = item.mediaName ?: "NA"
                        if (item.contentType == Constants.MEDIA_IMAGE) {
                            try {
                                val iFile = File(MainActivity.storageDir, "${Constants.PLAYLIST_DIR_NAME}/$itemName")
                                val bMap = BitmapFactory.decodeFile(iFile.toString())
                                if (bMap != null) {
                                    val isVertical = bMap.height > bMap.width
                                    glideHandler.obtainMessage(0, arrayOf(iFile, isVertical)).sendToTarget()
                                    for (i in 0 until (item.duration ?: 5)) {
                                        if (currentMedia != CURRENT_PLAYLIST) {
                                            break@inner
                                        }
                                        delay(1000)
                                    }
                                    if (currentMedia != CURRENT_PLAYLIST) {
                                        break@inner
                                    }
                                }
                            } catch (e: Exception) {
                                errorHandler.obtainMessage(0, "Error in Image: $e").sendToTarget()
                            }
                        }
                        if (item.contentType == Constants.MEDIA_VIDEO) {
                            val vFile = File(MainActivity.storageDir, "${Constants.PLAYLIST_DIR_NAME}/$itemName")
                            if (vFile.exists()) {
                                videoHandler.obtainMessage(0, vFile.toString()).sendToTarget()
                            }
                            for (i in 0 until (item.duration ?: 5)) {
                                if (currentMedia != CURRENT_PLAYLIST) {
                                    break@inner
                                }
                                delay(1000)
                            }
                            if (currentMedia != CURRENT_PLAYLIST) {
                                break@inner
                            }
                            videoHandler.obtainMessage(
                                0, Constants.STOP_VIDEO).sendToTarget()
                        }
                    }
                }
                imRunning = false
            }
        }

    private suspend fun playLayoutInMediaAsync(shareDataJson: String,
                                               isVertical1 : Boolean = false) =
        coroutineScope {
            async(Dispatchers.Main) {
                pImage.visibility = View.GONE
                pImage2.visibility = View.GONE
                pVideo.visibility = View.GONE
                customLayout.visibility = View.VISIBLE
                releaseYoutubePlayers()
                proBar.visibility = View.GONE
                customLayout.removeAllViews()
                if (mediaPlayer.isPlaying) {
                    try {
                        mediaPlayer.stop()
                    } catch (e:Exception) {
                    }
                }
                val gson = Gson()
                val typeT = object: TypeToken<List<CustomLayoutObject.LayoutInfo>>() {}.type
                val layoutList = gson.fromJson<List<CustomLayoutObject.LayoutInfo>>(shareDataJson, typeT)
         /*       val layoutList: List<CustomLayoutObject.LayoutInfo> =
                    Constants.getObjectFromJson(JSONArray(shareDataJson).toString())  */
                for(layout in layoutList) {
                    var layoutWidth : Int = (layout.width ?: 0).toInt()
                    var layoutHeight : Int = (layout.height ?: 0).toInt()
                    var layoutX : Int = (layout.x ?: 0).toInt()
                    var layoutY : Int = (layout.y ?: 0).toInt()
                    if (isVertical1) {
                        layoutWidth = (layout.height ?: 0).toInt()
                        layoutHeight = (layout.width ?: 0).toInt()
                        layoutX = (layout.y ?: 0).toInt()
                        layoutY = (layout.x ?: 0).toInt()
                    }
                    val linearLayout = LinearLayout(ctx)
                    val isVertical : Boolean = isVertical1
                    if (layout.opacity != null) {
                        linearLayout.alpha = layout.opacity ?: 1f
                    }
                    linearLayout.layoutParams = ConstraintLayout.LayoutParams(
                        (layoutWidth * wMulti).toInt(), (layoutHeight * hMulti).toInt()).apply {
                        when(Constants.rotationAngle) {
                            0f -> {
                                if (isVertical) {
                                    // not available
                                } else {
                                    startToStart = R.id.customLayout
                                    topToTop = R.id.customLayout
                                    marginStart = (layoutX * wMulti).toInt()
                                    topMargin = (layoutY * hMulti).toInt()
                                }
                            }
                            90f -> {
                                if (isVertical) {
                                    endToEnd = R.id.customLayout
                                    topToTop = R.id.customLayout
                                    marginEnd = (layoutX * wMulti).toInt()
                                    topMargin = (layoutY * hMulti).toInt()
                                } else {
                                    // not supported
                                }
                            }
                            180f -> {
                                if (isVertical) {
                                    // not supported
                                } else {
                                    endToEnd = R.id.customLayout
                                    bottomToBottom = R.id.customLayout
                                    marginEnd = (layoutX * wMulti).toInt()
                                    bottomMargin = (layoutY * hMulti).toInt()
                                }
                            }
                            270f -> {
                                if (isVertical) {
                                    startToStart = R.id.customLayout
                                    topToTop = R.id.customLayout

                                    marginStart = (layoutX * wMulti).toInt()
                                    topMargin =
                                        MainActivity.displayHeight - (layoutHeight * hMulti).toInt() - (layoutY * hMulti).toInt()
                                } else {
                                    // not supported
                                }
                            }
                        }
                    }
                    customLayout.addView(linearLayout)
                    //              customWebView = null
                    //              youView?.release()
                    //             youView = null
                    CoroutineScope(Dispatchers.Main).launch {
                        imRunning = true
                        // with new method assuming looping to be always true
                        playCustomMedia2Async(
                            linearLayout,
                            layout.media ?: emptyList(),
                            isVertical,
                                    0, false).await()
                        customContentFinished += 1
                    }
                }
            }
        }


    @SuppressLint("CheckResult", "SetJavaScriptEnabled")
    private suspend fun playCustomMedia2Async(
        layout: LinearLayout, mediaList: List<CustomLayoutObject.MediaInfo>,
            isVertical: Boolean, youtubeCount: Int, isCustomOrPlaylist : Boolean = true) =
        coroutineScope {
            async(Dispatchers.Main) {
                if (mediaList.size == 1) {
                    val mediaInfo = mediaList[0]
                    val fileName = mediaInfo.fileName
                    val mediaTime = ((mediaInfo.timeInSeconds?.toLong() ?: 10L) * 1000L)
                    when(mediaInfo.type) {
                        Constants.MEDIA_IMAGE -> {
                            val imageView = ImageView(ctx)
                            imageView.scaleType = ImageView.ScaleType.FIT_XY
                            imageView.layoutParams =
                                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT)
                            layout.addView(imageView)
                            try {
                                val imageFile = if (isCustomOrPlaylist) {
                                    File(MainActivity.storageDir, "${Constants.CUSTOM_CONTENT_DIR}/$fileName")
                                } else {
                                    File(MainActivity.storageDir, "${Constants.PLAYLIST_DIR_NAME}/$fileName")
                                }
                                val glide = Glide.with(ctx)
                                    .load(imageFile)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .override(layout.width,layout.height)
                                glide.transform(RotateTransformation(Constants.rotationAngle))
                                glide.into(imageView)
                            } catch (e: Exception) {
                            }
                        }
                        Constants.MEDIA_VIDEO -> {
                            val textureView = TextureView(ctx)
                            val mediaPlayer = MediaPlayer()
                            textureView.surfaceTextureListener =
                                object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        p0: SurfaceTexture,
                                        p1: Int,
                                        p2: Int
                                    ) {
                                        val surface = Surface(p0)
                                        mediaPlayer.setSurface(surface)
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        p0: SurfaceTexture,
                                        p1: Int,
                                        p2: Int
                                    ) {
                                    }

                                    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                                        return false
                                    }

                                    override fun onSurfaceTextureUpdated(p0: SurfaceTexture) { }
                                }
                            textureView.layoutParams = if (isVertical || Constants.rotationAngle == 90f || Constants.rotationAngle == 270f) {
                                LinearLayout.LayoutParams(
                                    layout.height, layout.width).apply {
                                    topMargin = (layout.height - layout.width) / 2
                                    marginStart = (layout.width - layout.height) / 2
                                }
                            } else {
                                LinearLayout.LayoutParams(
                                    layout.width, layout.height)
                            }
                            try {
                                layout.addView(textureView)
                                textureView.rotation = Constants.rotationAngle
                                val videoFile = if(isCustomOrPlaylist) {
                                    File(MainActivity.storageDir, "${Constants.CUSTOM_CONTENT_DIR}/$fileName")
                                } else {
                                    File(MainActivity.storageDir, "${Constants.PLAYLIST_DIR_NAME}/$fileName")
                                }
                                mediaPlayer.setDataSource(videoFile.toString())
                                mediaPlayer.setVolume(0f,0f)
                                mediaPlayer.isLooping = true
                                val timerRunnable = object : Runnable {
                                    override fun run() {
                                        try {
                                            mediaPlayer.seekTo(0)
                                            videoHandler.postDelayed(this, mediaTime)
                                        } catch (e: Exception) {
                                        }

                                    }
                                }
                                mediaPlayer.setOnPreparedListener {
                                    it.start()
                                    if (mediaPlayer.duration > mediaTime) {
                                        videoHandler.postDelayed(timerRunnable, mediaTime)
                                    }
                                }
                                mediaPlayer.setOnCompletionListener {
                                    mediaPlayer.stop()
                                    mediaPlayer.release()
                                    videoHandler.removeCallbacks(timerRunnable)
                                }
                                mediaPlayer.prepareAsync()
                                mediaPlayerList.add(mediaPlayer)
                            } catch (e: Exception) {
                                delay(1000)
                            }
                        }
                        Constants.MEDIA_YOUTUBE -> {
                    //        if (youView == null) {
                            val youView = YouTubePlayerView(ctx)
                            youView.layoutParams = LinearLayout.LayoutParams(
                                layout.width,
                                layout.height
                            )
                            Log.d("YoutubeView", "${layout.width} & ${layout.height}")
                            val videoURL = mediaInfo.fileName
                            val keyStartIndex = videoURL?.indexOf("watch?v=") ?: 0
                            val keyLength = 11
                            youView.enableAutomaticInitialization = false
                            youtubePlayerList.add(youView)
                            val videoKey = videoURL?.substring(keyStartIndex+8, keyStartIndex +8 + keyLength)
                            youView.initialize(object :
                                AbstractYouTubePlayerListener() {
                                override fun onReady(youTubePlayer: YouTubePlayer) {
                                    Log.d("YoutubePlayer", "ready to load video")
                                    youTubePlayer.loadVideo(videoKey ?: "null",0f)
                                    //resume video if last playtime stored previously
                                    var resumeSecond = MainActivity.sharedPreferences.getInt(
                                        videoKey, 0
                                    )
                                    if(youtubeCount > 1) youTubePlayer.mute()
                                    var recordSeconds = 0 // to resume youtube video on next play
                                    youTubePlayer.addListener(object : YouTubePlayerListener {

                                        var vidDuration = 0f

                                        override fun onApiChange(youTubePlayer: YouTubePlayer) { }

                                        override fun onCurrentSecond(
                                            youTubePlayer: YouTubePlayer,
                                            second: Float
                                        ) {
                                            recordSeconds++
                                            if(recordSeconds > 100) {
                                                recordSeconds = 0
                                                saveYoutubePlaytime(videoKey ?: "videoKeyUnknown", second.toInt())
                                                // note the play time every 10 seconds to resume play on next start
                                            }
                                        }

                                        override fun onError(
                                            youTubePlayer: YouTubePlayer,
                                            error: PlayerConstants.PlayerError
                                        ) {
                                            Log.e("YoutubeError", "$error")
                                        }

                                        override fun onPlaybackQualityChange(
                                            youTubePlayer: YouTubePlayer,
                                            playbackQuality: PlayerConstants.PlaybackQuality
                                        ) {
                                        }

                                        override fun onPlaybackRateChange(
                                            youTubePlayer: YouTubePlayer,
                                            playbackRate: PlayerConstants.PlaybackRate
                                        ) {
                                        }

                                        override fun onReady(youTubePlayer: YouTubePlayer) {

                                        }

                                        override fun onStateChange(
                                            youTubePlayer: YouTubePlayer,
                                            state: PlayerConstants.PlayerState
                                        ) {
                                            Log.d("Youtube", "$state")
                                            if(state == PlayerConstants.PlayerState.ENDED) {
                                                youTubePlayer.seekTo(0f)
                                            }
                                            if(state == PlayerConstants.PlayerState.PAUSED) {
                                               youTubePlayer.play()
                                            }
                                        }

                                        override fun onVideoDuration(
                                            youTubePlayer: YouTubePlayer,
                                            duration: Float
                                        ) {
                                            vidDuration = duration
                                            if(resumeSecond > 0) {
                                                youTubePlayer.seekTo(resumeSecond.toFloat())
                                                resumeSecond = 0
                                            }
                                        }

                                        override fun onVideoId(
                                            youTubePlayer: YouTubePlayer,
                                            videoId: String
                                        ) { }
                                        override fun onVideoLoadedFraction(
                                            youTubePlayer: YouTubePlayer,
                                            loadedFraction: Float
                                        ) { }
                                    })
                                }
                            })
                            youView.rotation = Constants.rotationAngle
                            layout.addView(youView)
                      /*      } else {
                                layout.addView(youView)
                            }
                            for (i in 0 until (mediaInfo.timeInSeconds ?: 5)) {
                                if (currentMedia != CURRENT_CUSTOM) {
                                    break
                                }
                                delay(1000)
                            }  */
                        }
                        Constants.MEDIA_WEB_PAGE, Constants.MEDIA_RSS-> {
                         //      if (customWebView == null) {
                           val customWebView = WebView(ctx)
                           customWebView.layoutParams = LinearLayout.LayoutParams(
                               layout.width,
                               layout.height
                           )
                           customWebView.settings.javaScriptEnabled = true
                           customWebView.settings.useWideViewPort = true
                           customWebView.settings.loadWithOverviewMode = true
                           customWebView.webViewClient = object : WebViewClient() {
                               override fun shouldOverrideUrlLoading(
                                   view: WebView?,
                                   request: WebResourceRequest?
                               ): Boolean {
                                   return false
                               }
                           }
                           customWebView.settings.allowContentAccess = true
                           customWebView.settings.domStorageEnabled = true
                           customWebView.loadUrl(mediaInfo.fileName ?: "NA")
                            customWebView.rotation = Constants.rotationAngle
                           layout.addView(customWebView)

                             /* }  else {
                                   layout.addView(customWebView)
                               }
                               for (i in 0 until (media.timeInSeconds ?: 10)) {
                                   if (currentMedia != CURRENT_CUSTOM) {
                                       break
                                   }
                                   delay(1000)
                               }    */
                        }
                        else -> { }
                    }
                } else {
                    val imageView = ImageView(ctx)
                    val imageView2 = ImageView(ctx)
                    imageView.scaleType = ImageView.ScaleType.FIT_XY
                    imageView2.scaleType = ImageView.ScaleType.FIT_XY
                    imageView.layoutParams =
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT)
                    imageView2.layoutParams =
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT)
                    val textureView = TextureView(ctx)
                    val mediaPlayer = MediaPlayer()
                    textureView.surfaceTextureListener =
                        object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                p0: SurfaceTexture,
                                p1: Int,
                                p2: Int
                            ) {
                                val surface = Surface(p0)
                                mediaPlayer.setSurface(surface)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                p0: SurfaceTexture,
                                p1: Int,
                                p2: Int
                            ) {

                            }

                            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                                return false
                            }

                            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) { }
                        }
                    textureView.layoutParams = if (isVertical || Constants.rotationAngle == 90f || Constants.rotationAngle == 270f) {
                        LinearLayout.LayoutParams(
                            layout.height,
                            layout.width
                        ).apply {
                            topMargin = (layout.height - layout.width) / 2
                            marginStart = (layout.width - layout.height) / 2
                        }
                    } else {
                        LinearLayout.LayoutParams(
                            layout.width, layout.height)
                    }
                    layout.addView(imageView)
                    layout.addView(imageView2)
                    layout.addView(textureView)
                    var evenImage = false
                    mediaPlayerList.add(mediaPlayer)
                    val mediaToObserve = if(isCustomOrPlaylist) CURRENT_CUSTOM else CURRENT_PLAYLIST
                    mainLoop@ while(currentMedia == mediaToObserve) {
                        for (media in mediaList) {
                            val fileName = media.fileName
                            val mediaTime = (media.timeInSeconds?.toLong() ?: 10000L) * 1000
                            when(media.type) {
                                Constants.MEDIA_IMAGE -> {
                                    try {
                                        textureView.visibility = View.GONE
                                        val imageFile = if (isCustomOrPlaylist) {
                                            File(MainActivity.storageDir,
                                                "${Constants.CUSTOM_CONTENT_DIR}/$fileName")
                                        } else {
                                            File(MainActivity.storageDir,
                                                "${Constants.PLAYLIST_DIR_NAME}/$fileName")
                                        }
                                        val glide = Glide.with(ctx)
                                            .load(imageFile)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                            .override(layout.width,layout.height)
                                        glide.transform(RotateTransformation(Constants.rotationAngle))
                                        if (evenImage) {
                                            glide.into(imageView)
                                            evenImage = false
                                            delay(500)
                                            imageView.visibility = View.VISIBLE
                                            imageView2.visibility = View.GONE
                                        } else {
                                            glide.into(imageView2)
                                            evenImage = true
                                            delay(500)
                                            imageView2.visibility = View.VISIBLE
                                            imageView.visibility = View.GONE
                                        }
                                        for (i in 0..(mediaTime/1000)) {
                                            if (currentMedia != mediaToObserve) {
                                                break@mainLoop
                                            }
                                            delay(1000)
                                        }
                                    } catch (e: Exception) {
                                        delay(mediaTime)
                                    }
                                }
                                Constants.MEDIA_VIDEO -> {
                                    try {
                                        videoHandler.postDelayed( {
                                            imageView.visibility = View.GONE
                                            imageView2.visibility = View.GONE
                                            textureView.visibility = View.VISIBLE
                                        }, 1000)
                                        textureView.rotation = Constants.rotationAngle
                                        val videoFile = if (isCustomOrPlaylist) {
                                            File(MainActivity.storageDir, "${Constants.CUSTOM_CONTENT_DIR}/$fileName")
                                        } else {
                                            File(MainActivity.storageDir, "${Constants.PLAYLIST_DIR_NAME}/$fileName")
                                        }
                                        mediaPlayer.setDataSource(videoFile.toString())
                                        mediaPlayer.setVolume(0f,0f)
                                        mediaPlayer.isLooping = true
                                        mediaPlayer.setOnPreparedListener {
                                            it.start()
                                        }
                                        mediaPlayer.setOnCompletionListener {
                                            mediaPlayer.stop()
                                        }
                                        mediaPlayer.prepareAsync()
                                        for (i in 0..(mediaTime/1000)) {
                                            if (currentMedia != CURRENT_CUSTOM) {
                                                break@mainLoop
                                            }
                                            delay(1000)
                                        }
                                        mediaPlayer.stop()
                                        mediaPlayer.reset()
                                    } catch (e: Exception) {
                                        delay(mediaTime)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    private fun clearMediaPlayers() {
        var released = 0
        for(player in mediaPlayerList) {
            try {
                player.stop()
                player.release()
                released += 1
            } catch (e: Exception) {
            }

        }
        mediaPlayerList.clear()
    }

    private fun setWeatherAndTimeLayout(wNtLayout: ConstraintLayout) {
        wNtLayout.removeAllViews()
  //      timeHandler.removeCallbacks(timeRunnable)
   //     areClocksRunning = false
        if(Constants.showWeather) {
  //          addInWeatherTimeLayout(Constants.weatherDataArray, wNtLayout, true)
            addInWeatherLayout2(Constants.weatherDataArray, wNtLayout)
        }
        if (Constants.showTime) {
            addInWeatherTimeLayout(Constants.dateTimeDataArray, wNtLayout)
        }
    }

    private fun addInWeatherLayout2(jsonArray: JSONArray, wNtLayout: ConstraintLayout) {
        for(i in 0 until jsonArray.length()) {
            val weatherText = TextView(ctx)
            val jsonObject : JSONObject = try {
                    jsonArray[i] as JSONObject
                } catch (e:Exception) { JSONObject() }
            val textX : Int=  try { jsonObject.get("x").toString().toInt()
            } catch (e:Exception) { 0 }
            val textY = try { jsonObject.get("y").toString().toInt()
            } catch (e:Exception) { 0 }
            val fontSize : Float = try {
                jsonObject.get("fontSize").toString().toFloat()
            } catch (e:Exception) {
                15f
            }
            val fontFamily = try {
                jsonObject.get("fontFamily").toString()
            } catch (e: Exception) {
                "Times New Roman"
            }
            val layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                when(Constants.rotationAngle) {
                    0f -> {
                        startToStart = R.id.weatherAndTimeLayout
                        topToTop = R.id.weatherAndTimeLayout
                        marginStart = (textX * wMulti).toInt()
                        topMargin = (textY * hMulti).toInt()
                    }
                    90f -> {
                        endToEnd = R.id.weatherAndTimeLayout
                        topToTop = R.id.weatherAndTimeLayout
                        marginEnd = (textY * wMulti).toInt()
                        topMargin = (textX * hMulti).toInt()
                    }
                    180f -> {
                        endToEnd = R.id.weatherAndTimeLayout
                        bottomToBottom = R.id.weatherAndTimeLayout
                        marginEnd = (textX * wMulti).toInt()
                        bottomMargin = (textY * hMulti).toInt()
                    }
                    270f -> {
                        startToStart = R.id.weatherAndTimeLayout
                        topToTop = R.id.weatherAndTimeLayout
                        marginStart = (textY * wMulti).toInt()
                        topMargin = MainActivity.displayHeight - (textX*hMulti).toInt()  - (fontSize * 3f).toInt()
                    }
                }
            }
            weatherText.layoutParams = layoutParams
            weatherText.includeFontPadding = false
            weatherText.rotation = Constants.rotationAngle
            val text = try { jsonObject.get("text").toString()
            } catch (e:Exception) { "" }
            val isInFern = text.contains("F")
            val textColor = getColorFromString( try
                { jsonObject.get("fill").toString()
                } catch (e:Exception) { "rgba(0, 0, 0, 1)" })
            weatherText.setTextColor(textColor)

            // try to get fontsize multiplier
//            1080i 320dpi  okay -> size/dpi = 3.375
//            720p 213dpi okay -> size/dpi = 3.38029
//
//            --large text
//                    720p 320dpi very large -> size/dpi = 2.25
//            720p 240dpi little big -> size/dpi = 3
//
//
//            size/dpi ratio small = large fonts
            weatherText.textSize = fontSize * fontSizeMultiplier
            val latLongString = try {
                jsonObject.get("link").toString()
            } catch (e:Exception) { ""}

            // set layout params for icon
            val weatherIconScaling : Float = when(MainActivity.displayHeight) {
                in 0..799 -> Constants.weatherIconScalingConstant720
                in 800..1200 -> Constants.weatherIconScalingConstant1080
                in 1200..2400 -> Constants.weatherIconScalingConstant4k
                else -> 1f
            }
            val weatherIcon = ImageView(ctx)
            val iconJson : JSONObject = try {
                    jsonObject.get("icon") as JSONObject
                } catch (e: Exception) {
                    JSONObject()
                }
            val iconX =  try { iconJson.get("x").toString().toFloat()
                } catch (e:Exception) { 0f }
            val iconY = try {
                iconJson.get("y").toString().toFloat()
                } catch (e:Exception) { 0f }
            val iconColor = getColorFromString(
                try { iconJson.get("fill").toString()
                } catch (e:Exception) { "rgba(0, 0, 0, 1)" })
            val iconScaleX : Float = try {
                iconJson.get("scaleX").toString().toFloat() * weatherIconScaling
            } catch (e: Exception) {
                1f
            }
            val iconScaleY : Float = try {
                iconJson.get("scaleY").toString().toFloat() * weatherIconScaling
            } catch (e: Exception) {
                1f
            }
            val iconSize = 30
            weatherIcon.imageTintList = ColorStateList.valueOf(iconColor)
            val layoutParamsIcon = ConstraintLayout.LayoutParams(
                iconSize,iconSize
            ).apply {
                when(Constants.rotationAngle) {
                    0f -> {
                        startToStart = R.id.weatherAndTimeLayout
                        topToTop = R.id.weatherAndTimeLayout
                        marginStart = (iconX * wMulti +
                                (((iconSize * iconScaleX) - iconSize) / 2)).toInt()
                        topMargin = (iconY * hMulti +
                                (((iconSize * iconScaleY) - iconSize)  / 2)).toInt()
                    }
                    90f -> {
                        endToEnd = R.id.weatherAndTimeLayout
                        topToTop = R.id.weatherAndTimeLayout
                        marginEnd = (iconY * wMulti  +
                                (((iconSize * iconScaleY) - iconSize)  / 2)).toInt()
                        topMargin = (iconX * hMulti +
                                (((iconSize * iconScaleX) - iconSize) / 2)).toInt()
                    }
                    180f -> {
                        endToEnd = R.id.weatherAndTimeLayout
                        bottomToBottom = R.id.weatherAndTimeLayout
                        marginEnd = (iconX * wMulti +
                                (((iconSize * iconScaleX) - iconSize) / 2)).toInt()
                        bottomMargin = (iconY * hMulti +
                                (((iconSize * iconScaleY) - iconSize) / 2)).toInt()
                    }
                    270f -> {
                        startToStart = R.id.weatherAndTimeLayout
                        topToTop = R.id.weatherAndTimeLayout
                        marginStart = (iconY * wMulti +
                                (((iconSize * iconScaleY) - iconSize) / 2)).toInt()
                        topMargin = MainActivity.displayHeight - (iconX*hMulti +
                                (((iconSize * iconScaleX) - iconSize) / 2)).toInt()  - (fontSize * 3f).toInt()
                    }
                }
            }
            weatherIcon.layoutParams = layoutParamsIcon
            weatherIcon.scaleX = iconScaleX
            weatherIcon.scaleY = iconScaleY
            weatherIcon.rotation = Constants.rotationAngle

            CoroutineScope(Dispatchers.Main).launch {
                val weatherArray = getWeatherFromApiAsync(latLongString).await()
                weatherText.text =if (isInFern) {
                    "${Constants.convertCelcToFern(weatherArray[1].toFloat())}??F"
                } else {
                    "${weatherArray[1]}??C"
                }
                val isNight : Boolean = weatherArray[2].toBoolean()
                val iconDrawable : Int = when(weatherArray[0]) {
                    "0" , "1" -> if (isNight) R.drawable.moon_star else R.drawable.sunny
                    "2" , "3" -> if (isNight) R.drawable.cloudy_moon_1 else R.drawable.partly_sunny
                    "45" , "48" -> R.drawable.haze
                    "51", "53", "55" -> R.drawable.haze
                    "61" -> R.drawable.light_rain
                    "63", "65" -> R.drawable.rain
                    "66", "67" -> R.drawable.rain
                    "71", "73", "75", "77", "85", "86" -> R.drawable.snow
                    "80", "81" -> R.drawable.rain
                    "82", "95", "96", "99" -> R.drawable.thunderstrom
                    else -> R.drawable.sunny
                }
                weatherIcon.setImageDrawable(
                    ResourcesCompat.getDrawable(ctx.resources,
                        iconDrawable , ctx.theme))
                wNtLayout.addView(weatherIcon)
            }
            setSelectedFonts(fontFamily, weatherText)
            wNtLayout.addView(weatherText)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addInWeatherTimeLayout(jsonArray: JSONArray, wNtLayout: ConstraintLayout) {
        allClocks.clear()
        for(i in 0 until jsonArray.length()) {
            val timeText = TextView(ctx)
            val jsonObject = jsonArray[i] as JSONObject
            val x = jsonObject.get("x").toString().toInt()
            val y = jsonObject.get("y").toString().toInt()
            val text = jsonObject.get("text").toString()
            val timeInAMPM = text.contains(" AM") || text.contains(" PM")
            val isTimeElseDate = text.contains(":")
            val textColor = getColorFromString(jsonObject.get("fill").toString())
            timeText.setTextColor(textColor)
            timeText.text = "---"
            val fontSize = jsonObject.get("fontSize").toString().toFloat()
            val fontFamily = jsonObject.get("fontFamily").toString()
            val bgColorString: String = try {
                jsonObject.get("backgroundColor").toString()
            } catch (e: Exception) {
                "rgba(0,0,0,0)"
            }
            val bgColor = getColorFromString(bgColorString)
            timeText.textSize = fontSize * fontSizeMultiplier
            timeText.includeFontPadding = false
            timeText.setLineSpacing(Constants.weatherAndTimeTextLineSpacing1080, 1f)
            val layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                when(Constants.rotationAngle) {
                    0f -> {
                        startToStart = R.id.weatherAndTimeLayout
                        topToTop = R.id.weatherAndTimeLayout
                        marginStart = (x * wMulti).toInt()
                        topMargin = (y * hMulti).toInt()
                    }
                    90f -> {
                        endToEnd = R.id.weatherAndTimeLayout
                        topToTop = R.id.weatherAndTimeLayout
                        marginEnd = (x * wMulti).toInt()
                        topMargin = (y * hMulti).toInt()
                    }
                    180f -> {
                        endToEnd = R.id.weatherAndTimeLayout
                        bottomToBottom = R.id.weatherAndTimeLayout
                        marginEnd = (x* wMulti).toInt()
                        bottomMargin = (y * hMulti).toInt()
                    }
                    270f -> {
                        startToStart = R.id.weatherAndTimeLayout
                        topToTop = R.id.weatherAndTimeLayout
                        marginStart = (x * wMulti).toInt()
                        topMargin = MainActivity.displayHeight - (y*hMulti).toInt() - (fontSize * 6f).toInt()
                    }
                }
            }
            timeText.layoutParams = layoutParams
            timeText.rotation = Constants.rotationAngle
                val timeZoneString  = jsonObject.get("link").toString()
                CoroutineScope(Dispatchers.Main).launch {
                    val time = getTimeFromAPIAsync(timeZoneString).await()
                    if (time > 0) {
                        allClocks.add(ClockObject(time, timeText, !timeInAMPM, bgColor, isTimeElseDate))
                    }
                }
            val shadowStr = jsonObject.get("shadow").toString()
            if (shadowStr.isNotBlank() && shadowStr != "null") {
                try {
                    val shadowObject = jsonObject.get("shadow") as JSONObject
                    val shadowColor = getColorFromString(shadowObject.get("color").toString())
                    val blur = shadowObject.get("blur").toString().toFloat()
                    val ofX = shadowObject.get("offsetX").toString().toFloat()
                    val ofY = shadowObject.get("offsetY").toString().toFloat()
                    timeText.setShadowLayer(blur, ofX, ofY, shadowColor)
                } catch (e: Exception) {
                }
            }
            setSelectedFonts(fontFamily, timeText) // for testing only
            wNtLayout.addView(timeText)
        }
        timeHandler.removeCallbacks(timeRunnable)
        areClocksRunning = true
        timeHandler.postDelayed(timeRunnable, 2000)
    }

    private fun getColorFromString(str: String): Int {
        val index = str.indexOf("(")
        var color = 0
        if(index > 0) {
            var finalString = str.substring(index +1, str.length - 1)
            finalString.replace(" ", "").also { finalString = it }
            val colorsArray = finalString.split(",")
            color = Color.argb(
                (colorsArray[3].toFloat() * 255).toInt(),
                colorsArray[0].toInt(),
                colorsArray[1].toInt(),
                colorsArray[2].toInt()
            )
        }
        return color
    }

    private suspend fun getWeatherFromApiAsync(latLong: String) : Deferred<Array<String>> =
        coroutineScope {
            async(Dispatchers.IO) {
                try {
                    val latLongSplits = latLong.split(",")
                    if (latLongSplits.size < 2) return@async arrayOf("Error", "err")
                    val latitude = latLongSplits[0]
                    val longitude = latLongSplits[1]
                    val apiURL = URL("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true&daily=sunrise,sunset&timezone=GMT")
                    val connection = apiURL.openConnection()
                    connection.connect()
                    val apiStream = connection.getInputStream()
                    val buff = ByteArray(4096)
                    val read = apiStream.read(buff)
                    val finalString = String(buff,0, read)
                    try {
                        val weatherJson = JSONObject(finalString)
                        val currentWeatherJson = weatherJson.get("current_weather") as JSONObject
                        val temperature = currentWeatherJson.get("temperature")
                        val weatherCode = currentWeatherJson.get("weathercode")
                        val dailyJson = weatherJson.get("daily") as JSONObject
                        val sunriseSet = dailyJson.get("sunrise") as JSONArray
                        val sunsetSet = dailyJson.get("sunset") as JSONArray
                        val sunriseTimeUtc = sunriseSet[0].toString().split("T")[1]
                        val sunsetTimeUtc = sunsetSet[0].toString().split("T")[1]
                        val cal =  Calendar.getInstance()
                        val currentUtcInMill = cal.timeInMillis - cal.timeZone.rawOffset
                        cal.timeInMillis = currentUtcInMill
                        val currentUtcHour = cal.get(Calendar.HOUR_OF_DAY)
                        val currentUtcMin = cal.get(Calendar.MINUTE)
                        val currentUTCHrStr = if(currentUtcHour < 10) {
                            "0$currentUtcHour"
                        } else { "$currentUtcHour" }
                        val currentUtcMinStr = if (currentUtcMin < 10) {
                            "0$currentUtcMin"
                        } else { "$currentUtcMin" }
                        val currentUtcTimeStr = "$currentUTCHrStr:$currentUtcMinStr"
                        val night:Boolean = if (sunriseTimeUtc > sunsetTimeUtc) {
                            sunriseTimeUtc < currentUtcTimeStr && currentUtcTimeStr < sunriseTimeUtc
                        } else {
                            !(sunriseTimeUtc < currentUtcTimeStr && currentUtcTimeStr < sunsetTimeUtc)
                        }
                        return@async arrayOf(weatherCode.toString() ,temperature.toString(), night.toString())
                    } catch (e: Exception) {
                        return@async arrayOf("error", "error")
                    }
                } catch (e: Exception) {
                    return@async arrayOf("Error", "$e")
                }
            }
        }

    private suspend fun getTimeFromAPIAsync(timeZone: String) : Deferred<Long> =
        coroutineScope {
            async(Dispatchers.IO) {
                try {
                    val cal = Calendar.getInstance()
                    if(timeZone.lowercase() == cal.timeZone.id.lowercase()) {
                        return@async cal.timeInMillis
                    } else {
                        val url = URL("http://worldtimeapi.org/api/timezone/$timeZone")
                        val connection = url.openConnection()
                        connection.connect()
                        val inputStream = connection.getInputStream()
                        val buf = ByteArray(4096)
                        val read = inputStream.read(buf)
                        val apiRes = String(buf, 0, read)
                        val apiJson = JSONObject(apiRes)
                        val utcOffsetString = apiJson.get("utc_offset").toString()
                        val utcTime = cal.timeInMillis - cal.timeZone.rawOffset
                        val utcOffsetPositiveStr = utcOffsetString.substring(1)
                        val offsetHour = utcOffsetPositiveStr.split(":")[0]
                        val offsetMinute = utcOffsetPositiveStr.split(":")[1]
                        var millOffset = offsetHour.toInt() * 3600000
                        millOffset += (offsetMinute.toInt() * 60000)
                        val timeToDisplayMill = if (utcOffsetString.first() == '-') {
                            utcTime - millOffset
                        } else {
                            utcTime + millOffset
                        }
                        return@async timeToDisplayMill
                    }
                } catch (e: Exception) {
                    return@async -1
                }
            }
        }

    private fun setSelectedFonts(font: String, textView : TextView) {
        val typeface : Typeface? =
        when(font) {
            "Arial" -> {
                Typeface.createFromAsset(ctx.assets, "times.ttf")
            }
            "Roboto" -> {
                null
            }
            "Lato" -> {
                Typeface.createFromAsset(ctx.assets, "lato.ttf")
            }
            "Montserrat" -> {
                Typeface.createFromAsset(ctx.assets, "montserrat.ttf")
            }
            "Open Sans" -> {
                Typeface.createFromAsset(ctx.assets, "open_sans.ttf")
            }
            "Oswald" -> {
                Typeface.createFromAsset(ctx.assets, "oswald.ttf")
            }
            "Raleway" -> {
                Typeface.createFromAsset(ctx.assets, "raleway.ttf")
            }
            "Audiowide" -> {
                Typeface.createFromAsset(ctx.assets, "audiowide.ttf")
            }
            "Assistant" -> {
                Typeface.createFromAsset(ctx.assets, "assistant.ttf")
            }
            "Apollos Mum" -> {
                Typeface.createFromAsset(ctx.assets, "apollos_mum.ttf")
            }
            "Chocolicious" -> {
                Typeface.createFromAsset(ctx.assets, "chocolicious.ttf")
            }
            "Dinomiko" -> {
                Typeface.createFromAsset(ctx.assets, "dinomiko.otf")
            }
            else -> {
                Typeface.createFromAsset(ctx.assets, "times.ttf")
            }
        }
        if(typeface != null) textView.typeface = typeface
    }


    class DisplayItems(val name: String)

    class RotateTransformation(private val rotationAngle : Float) : BitmapTransformation() {

        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            messageDigest.update(("rotate$rotationAngle").toByte())
        }

        override fun transform(
            pool: BitmapPool,
            toTransform: Bitmap,
            outWidth: Int,
            outHeight: Int
        ): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(rotationAngle)
            return Bitmap.createBitmap(toTransform, 0, 0, toTransform.width, toTransform.height, matrix, true)
        }
    }

    private fun releaseYoutubePlayers() {
        var released = 0
        for(player in youtubePlayerList) {
            player.release()
            released++
        }
        Log.d("youtubePlayer", "released $released")
        youtubePlayerList.clear()
    }

    private fun saveYoutubePlaytime(videoId: String, seconds: Int) {
        MainActivity.sharedPreferences.edit().putInt(
            videoId, seconds).apply()
    }

    class ClockObject(var time: Long, val textView: TextView, val is24Hours: Boolean, val bgColor: Int, val isTimeElseDate : Boolean)

    companion object {
        const val PLAY_TYPE_IMAGE = 0
        const val PLAY_TYPE_VIDEO = 1
        const val PLAY_TYPE_LAYOUT = 2
        var playType : Int = PLAY_TYPE_IMAGE
        @SuppressLint("StaticFieldLeak")
        var playlistTextureView : TextureView? = null
    }
}