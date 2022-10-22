package com.nento.player.app.fragment

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
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
    private var customWebView : WebView? = null
    private var youView : YouTubePlayerView? = null
    private val mediaPlayerList = ArrayList<MediaPlayer>()
    private lateinit var mediaPlayer : MediaPlayer
    private var totalCustomContent = 0
    private var customContentFinished = 0
    private var areClocksRunning = false
    private var timeInAMPM = false

    private lateinit var glideHandler: Handler
    private lateinit var timeHandler : Handler
    private lateinit var timeRunnable: Runnable
    private val allClocks = ArrayList<ClockObject>()
    private val wMulti: Float = (MainActivity.displayWidth.toFloat() / Constants.CUSTOM_WIDTH_THEIR)
    private val hMulti: Float = (MainActivity.displayHeight.toFloat() / Constants.CUSTOM_HEIGHT_THEIR)

    @SuppressLint("SetJavaScriptEnabled", "CheckResult")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ctx = findNavController().context
        Constants.onSplashScreen = false
        val rootView = inflater.inflate(R.layout.fragment_media, container, false)
        val pImage = rootView.findViewById<ImageView>(R.id.previewImage)
        val pImage2 = rootView.findViewById<ImageView>(R.id.previewImage2)
        val pVideo = rootView.findViewById<TextureView>(R.id.textureVideo)
        val weatherAndTimeLayout = rootView.findViewById<ConstraintLayout>(R.id.weatherAndTimeLayout)
     //   val weatherText = rootView.findViewById<TextView>(R.id.weatherText)
        mediaPlayer = MediaPlayer()
        var isEvenImage = false
        val customLayout = rootView.findViewById<ConstraintLayout>(R.id.customLayout)
        playlistTextureView = pVideo
        playlistTextureView?.surfaceTextureListener = this
        val screenId = rootView.findViewById<TextView>(R.id.screenIdText)
        val proBar = rootView.findViewById<ProgressBar>(R.id.mediaProgressBar)
        screenId.text = Constants.screenID

        timeRunnable = object : Runnable {
            val cal = Calendar.getInstance()
            override fun run() {
                for(clock in allClocks) {
                    cal.timeInMillis = clock.time
                    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
                    val hour12 = cal.get(Calendar.HOUR)
                    val amPm = cal.get(Calendar.AM_PM)
                    var min = cal.get(Calendar.MINUTE).toString()
                    if (min.length == 1) {
                        min = "0$min"
                    }
                    val dayName = cal.get(Calendar.DAY_OF_WEEK)
                    val month = cal.get(Calendar.MONTH)
                    val date = cal.get(Calendar.DATE)
                    val year = cal.get(Calendar.YEAR)
                    Log.d("TimeRunnable", "updated")
                    val clockText : String = if (timeInAMPM) {
                        var amPMS = "AM"
                        if (amPm == 1) {
                            amPMS = "PM"
                        }
                        "$hour12:$min $amPMS, ${Constants.getDayNameFromCal(dayName)}\n${Constants.getMonthNameFromCal(month)} $date, $year"
                    } else {
                        "$hour24:$min, ${Constants.getDayNameFromCal(dayName)}\n${Constants.getMonthNameFromCal(month)} $date, $year"
                    }
                    clock.textView.text = clockText
                    clock.time += 30000
                }
                timeHandler.postDelayed(this, 30000)
            }
        }
        timeHandler = Handler(Looper.getMainLooper())
        errorHandler = Handler(Looper.getMainLooper()) {
            val msg = it.obj as String
            Log.e("MediaError", msg)
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
            customLayout.removeAllViews()
            if ((Constants.rotationAngel == 0f
                        || Constants.rotationAngel == 180f)
                && !isVertical) {
                Log.d("FitXY horizontal", "------------------------")
                if (isEvenImage) {
                    pImage2.scaleType = ImageView.ScaleType.FIT_XY
                } else {
                    pImage.scaleType = ImageView.ScaleType.FIT_XY
                }
            }
            if ((Constants.rotationAngel == 0f
                        || Constants.rotationAngel == 180f)
                && isVertical) {
                Log.d("FitCenter Vertical", "--------------------------")
                if (isEvenImage) {
                    pImage2.scaleType = ImageView.ScaleType.FIT_CENTER
                } else {
                    pImage.scaleType = ImageView.ScaleType.FIT_CENTER
                }
            }
            if ((Constants.rotationAngel == 90f
                        || Constants.rotationAngel == 270f)
                && isVertical) {
                Log.d("FitXY vertical", "-----------------------------")
                if (isEvenImage) {
                    pImage2.scaleType =ImageView.ScaleType.FIT_XY
                } else {
                    pImage.scaleType = ImageView.ScaleType.FIT_XY
                }
            }
            if ((Constants.rotationAngel == 90f
                        || Constants.rotationAngel == 270f)
                && !isVertical) {
                Log.d("FitCenter Horizontal", "----------------------------")
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
            if (Constants.rotationAngel > 0f) {
                glide.override(MainActivity.displayHeight, MainActivity.displayWidth)
                glide.transform(RotateTransformation(Constants.rotationAngel))
            } else {
                glide.override(MainActivity.displayWidth, MainActivity.displayHeight)
            }
            glide.override(MainActivity.displayWidth, MainActivity.displayHeight)
            if (isEvenImage) {
                glide.into(pImage2)
                isEvenImage = false
                CoroutineScope(Dispatchers.Main).launch {
                    delay(500)
                    Log.d("ShowingImage", "Even")
                    pImage.visibility = View.GONE
                    pImage2.visibility = View.VISIBLE
                }
            } else {
                glide.into(pImage)
                isEvenImage = true
                CoroutineScope(Dispatchers.Main).launch {
                    delay(500)
                    Log.d("ShowingImage", "Odd")
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
                    Log.e("ErrorPlaylistVideo", "$e")
                }
            }
            true
        }
        videoHandler = Handler(Looper.getMainLooper()) {
            val file = it.obj as String
            Log.d("VideoHandler", file)
            if (file == Constants.STOP_VIDEO) {
                try {
                    mediaPlayer.stop()
                } catch (e:Exception) {
                    Log.e("ErrorPlaylistVideo", "$e")
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
                customLayout.removeAllViews()
            } ,2000)  // video play needs some time directly showing video/textureView
            // make the app frozen for few moment. that's why it delayed
            Log.d("FileToPlay", file)
            true
        }

        customLayoutHandler = Handler(Looper.getMainLooper()) {
            playType = PLAY_TYPE_LAYOUT
            pImage.visibility = View.GONE
            pImage2.visibility = View.GONE
            pVideo.visibility = View.GONE
            customLayout.visibility = View.VISIBLE
            customLayout.removeAllViews()
            proBar.visibility = View.GONE
            if (mediaPlayer.isPlaying) {
                try {
                    mediaPlayer.stop()
                } catch (e:Exception) {
                    Log.e("ErrorPlaylistVideo", "$e")
                }
            }
            try {
                //convert jsonData to Object
                val gson = Gson()
                val typeT = object : TypeToken<CustomLayoutObject>() { }
                val customInfoFile = File(MainActivity.storageDir, Constants.CUSTOM_LAYOUT_JSON_NAME)
                val jsonStr = customInfoFile.readText()
                val dLayoutObject = gson.fromJson<CustomLayoutObject>(jsonStr, typeT.type)
                Log.d("CustomLayouts", "${dLayoutObject.layout?.size}")

                imRunning = true
                Log.d("CustomRunning", "started")

                totalCustomContent = dLayoutObject.layout?.size ?: 0
                customContentFinished = 0
                if (dLayoutObject.isVertical == true) {
                    if (Constants.rotationAngel == 0f) {
                        Constants.rotationAngel = 90f
                    } else if (Constants.rotationAngel == 180f) {
                        Constants.rotationAngel = 270f
                    }
                } else {
                    if (Constants.rotationAngel == 90f) {
                        Constants.rotationAngel = 180f
                    } else if (Constants.rotationAngel == 270f) {
                        Constants.rotationAngel = 0f
                    }
                }
                MainActivity.sharedPreferences.edit().putFloat(
                    Constants.PREFS_ROTATION_ANGLE, Constants.rotationAngel).apply()
                Log.d("CustomRotationSet", "${Constants.rotationAngel}")
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
                        when(Constants.rotationAngel) {
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
                    customWebView = null
                    youView?.release()
                    youView = null
                    CoroutineScope(Dispatchers.Main).launch {
                        imRunning = true
                        // with new method assuming looping to be always true
                        playCustomMedia2Async(linearLayout, layout.media ?: emptyList(), dLayoutObject.isVertical ?: false).await()
                        Log.d("CustomRunning", "one finished")
                        customContentFinished += 1
                    }
                }
                Log.d("CustomLayout", "All layout set for new Method")
            } catch (e: Exception) {
                Log.e("CustomError", "$e")
                errorHandler.obtainMessage(0, "Error: $e").sendToTarget()
            }
            true
        }
        val updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                Log.d("FragmentMedia", "Media change update")
                currentMedia = -1
                CoroutineScope(Dispatchers.Main).launch {
                    proBar.visibility = View.VISIBLE
                    while (imRunning) {
                        Log.d("ImRunning", "Waiting for current media to stop")
                        Log.d("ImRunning", "customContentFinished : $customContentFinished / $totalCustomContent")
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
                            val itemsInPlaylist = p1.getStringArrayExtra(Constants.C_PLAYLIST)
                            for (item in itemsInPlaylist ?: emptyArray()) {
                                Log.d("MediaPlaylist", item)
                            }
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
                            Log.d("CustomLayout", "Broadcast Received")
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
        Log.d("SurfaceTexture", "Available")
        val surface = Surface(p0)
        mediaPlayer.setSurface(surface)
        Log.d("SurfaceTexture", "MediaPlayer set")
    }

    private fun playOnMediaPlayer(file: String) {
        mediaPlayer.reset()
        mediaPlayer.setDataSource(file)
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            it.start()
        }
        mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
            if (Constants.rotationAngel == 90f || Constants.rotationAngel == 270f) {
                playVideoVertically(width, height, playlistTextureView)
            } else {
                playVideoNormally(width, height, playlistTextureView)
            }
        }
        mediaPlayer.setOnCompletionListener {
            mediaPlayer.stop()
        }
    }

    override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
        Log.d("SurfaceTexture", "SizeChanged")
    }

    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
        Log.d("SurfaceTexture", "Destroyed")
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
        textureView?.rotation = Constants.rotationAngel
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
        textureView?.rotation = Constants.rotationAngel
    }

    private fun generateDisplayItemList() {
        itemArray.clear()
        val allRecords = RecordsAsJSON.getAllRecords()
        val records = allRecords.keys()
        for (key in records) {
            val pFile = File(MainActivity.storageDir, key)
            if (pFile.exists()) {
                Log.d("CheckingFile", "$key available")
                itemArray.add(DisplayItems(key))
                //   , allRecords.get(key) as Int))
            } else {
                Log.e("CheckingFile", "$key not available")
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
                            Log.d("PlayerSkipping", "${item.mediaName}")
                            continue@inner
                        }
                        val itemName : String = item.mediaName ?: "NA"
                        if (item.contentType == Constants.MEDIA_IMAGE) {
                            try {
                                val iFile = File(MainActivity.storageDir, "${Constants.PLAYLIST_DIR_NAME}/$itemName")
                                val bMap = BitmapFactory.decodeFile(iFile.toString())
                                if (bMap != null) {
                                    Log.d("PlaylistImage", "Width: ${bMap.width}")
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
                            Log.d("TestingVideoDuration", "${item.duration}")
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
                    Log.d("PlaylistLoop", "inner terminated")
                }
                Log.d("PlaylistLoop", "outer terminated")
                imRunning = false
            }
        }


    @SuppressLint("CheckResult")
    private suspend fun playCustomMedia2Async(
        layout: LinearLayout,
        mediaList: List<CustomLayoutObject.MediaInfo>, isVertical: Boolean) =
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
                                val imageFile = File(MainActivity.storageDir, "${Constants.CUSTOM_CONTENT_DIR}/$fileName")
                                val glide = Glide.with(ctx)
                                    .load(imageFile)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .override(layout.width,layout.height)
                                glide.transform(RotateTransformation(Constants.rotationAngel))
                                glide.into(imageView)
                            } catch (e: Exception) {
                                Log.e("CustomView", "$e at $fileName")
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
                                        Log.d("CustomVideo", "Surface Set")
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        p0: SurfaceTexture,
                                        p1: Int,
                                        p2: Int
                                    ) {
                                        Log.d("CustomVideo", "Surface SizeChanged")
                                    }

                                    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                                        Log.d("CustomVideo", "Texture Destroyed")
                                        return false
                                    }

                                    override fun onSurfaceTextureUpdated(p0: SurfaceTexture) { }
                                }
                            textureView.layoutParams = if (isVertical || Constants.rotationAngel == 90f || Constants.rotationAngel == 270f) {
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
                                textureView.rotation = Constants.rotationAngel
                                val videoFile = File(MainActivity.storageDir, "${Constants.CUSTOM_CONTENT_DIR}/$fileName")
                                mediaPlayer.setDataSource(videoFile.toString())
                                mediaPlayer.setVolume(0f,0f)
                                mediaPlayer.isLooping = true
                                val timerRunnable = object : Runnable {
                                    override fun run() {
                                        try {
                                            Log.d("MediaPlayer11","trying to seek to 0")
                                            mediaPlayer.seekTo(0)
                                            videoHandler.postDelayed(this, mediaTime)
                                        } catch (e: Exception) {
                                            Log.e("MediaPlayer11", "replay error: $e")
                                        }

                                    }
                                }
                                mediaPlayer.setOnPreparedListener {
                                    Log.d("CustomVideo", "MediaPlayer Set for $videoFile")
                                    it.start()
                                    Log.d("MediaPlayer11","${mediaPlayer.duration}")
                                    if (mediaPlayer.duration > mediaTime) {
                                        videoHandler.postDelayed(timerRunnable, mediaTime)
                                        Log.d("MediaPlayer11","posting runnable in $mediaTime")
                                    }
                                }
                                mediaPlayer.setOnCompletionListener {
                                    mediaPlayer.stop()
                                    mediaPlayer.release()
                                    videoHandler.removeCallbacks(timerRunnable)
                                    Log.d("CustomVideo", "MediaPlayer released")
                                }
                                mediaPlayer.prepareAsync()
                                mediaPlayerList.add(mediaPlayer)
                            } catch (e: Exception) {
                                Log.e("CustomVideoError", "$e")
                                delay(1000)
                            }
                        }
                        Constants.MEDIA_WEB_PAGE -> {
                            /*   if (customWebView == null) {
                                   customWebView = WebView(ctx)
                                   customWebView?.layoutParams = LinearLayout.LayoutParams(
                                       layout.width,
                                       layout.height
                                   )
                                   customWebView?.settings?.javaScriptEnabled = true
                                   customWebView?.settings?.useWideViewPort = true
                                   customWebView?.settings?.loadWithOverviewMode = true
                                   customWebView?.webViewClient = object : WebViewClient() {
                                       override fun shouldOverrideUrlLoading(
                                           view: WebView?,
                                           request: WebResourceRequest?
                                       ): Boolean {
                                           return false
                                       }
                                   }
                                   customWebView?.settings?.allowContentAccess = true
                                   customWebView?.settings?.domStorageEnabled = true
                                   customWebView?.loadUrl(media.fileName ?: "NA")
                                   layout.addView(customWebView)
                               } else {
                                   layout.addView(customWebView)
                               }
                               for (i in 0 until (media.timeInSeconds ?: 10)) {
                                   if (currentMedia != CURRENT_CUSTOM) {
                                       break
                                   }
                                   delay(1000)
                               }  */
                        }
                        Constants.MEDIA_YOUTUBE -> {
                            /*    if (youView == null) {
                                    youView = YouTubePlayerView(ctx)
                                    youView?.layoutParams = LinearLayout.LayoutParams(
                                        layout.width,
                                        layout.height
                                    )
                                    val videoURL = media.fileName
                                    val keyStartIndex = videoURL?.indexOf("watch?v=") ?: 0
                                    val keyLength = 11
                                    val videoKey = videoURL?.substring(keyStartIndex+8, keyStartIndex +8 + keyLength)
                                    youView?.addYouTubePlayerListener(object :
                                        AbstractYouTubePlayerListener() {
                                        override fun onReady(youTubePlayer: YouTubePlayer) {
                                            Log.d("YOUTUBEPlayer", "Ready")
                                            youTubePlayer.loadVideo(videoKey ?: "null",0f)
                                            youTubePlayer.addListener(object : YouTubePlayerListener {

                                                var vidDuration = 0f

                                                override fun onApiChange(youTubePlayer: YouTubePlayer) { }

                                                override fun onCurrentSecond(
                                                    youTubePlayer: YouTubePlayer,
                                                    second: Float
                                                ) {
                                                    Log.d("CurrentSecond", "$second")
                                                    if (vidDuration > 0 && (second >= (vidDuration - 1))) {
                                                        youView?.release()
                                                        youView = null
                                                    }
                                                }

                                                override fun onError(
                                                    youTubePlayer: YouTubePlayer,
                                                    error: PlayerConstants.PlayerError
                                                ) {
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
                                                    Log.d("YOUTUBE", "Ready")
                                                }

                                                override fun onStateChange(
                                                    youTubePlayer: YouTubePlayer,
                                                    state: PlayerConstants.PlayerState
                                                ) {
                                                }

                                                override fun onVideoDuration(
                                                    youTubePlayer: YouTubePlayer,
                                                    duration: Float
                                                ) {
                                                    vidDuration = duration
                                                    Log.d("YOUTUBE", "Duration: $duration")
                                                }

                                                override fun onVideoId(
                                                    youTubePlayer: YouTubePlayer,
                                                    videoId: String
                                                ) {

                                                }

                                                override fun onVideoLoadedFraction(
                                                    youTubePlayer: YouTubePlayer,
                                                    loadedFraction: Float
                                                ) {
                                                }

                                            })
                                        }
                                    })
                                    layout.addView(youView)
                                } else {
                                    layout.addView(youView)
                                }
                                for (i in 0 until (media.timeInSeconds ?: 5)) {
                                    if (currentMedia != CURRENT_CUSTOM) {
                                        break
                                    }
                                    delay(1000)
                                }  */
                        }
                        else -> { Log.e("CustomLayout", "Unknown media type")}
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
                                Log.d("CustomVideo", "Surface Set")
                            }

                            override fun onSurfaceTextureSizeChanged(
                                p0: SurfaceTexture,
                                p1: Int,
                                p2: Int
                            ) {
                                Log.d("CustomVideo", "Surface SizeChanged")
                            }

                            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                                Log.d("CustomVideo", "Texture Destroyed")
                                return false
                            }

                            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) { }
                        }
                    textureView.layoutParams = if (isVertical || Constants.rotationAngel == 90f || Constants.rotationAngel == 270f) {
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
                    mainLoop@ while(currentMedia == CURRENT_CUSTOM) {
                        for (media in mediaList) {
                            val fileName = media.fileName
                            val mediaTime = (media.timeInSeconds?.toLong() ?: 10000L) * 1000
                            when(media.type) {
                                Constants.MEDIA_IMAGE -> {
                                    try {
                                        textureView.visibility = View.GONE
                                        val imageFile = File(
                                            MainActivity.storageDir,
                                            "${Constants.CUSTOM_CONTENT_DIR}/$fileName"
                                        )
                                        val glide = Glide.with(ctx)
                                            .load(imageFile)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                            .override(layout.width,layout.height)
                                        glide.transform(RotateTransformation(Constants.rotationAngel))
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
                                            if (currentMedia != CURRENT_CUSTOM) {
                                                Log.d("CustomLayout", "Stopping main loop")
                                                break@mainLoop
                                            }
                                            delay(1000)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CustomLayout", "in looping images: $e")
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
                                        textureView.rotation = Constants.rotationAngel
                                        val videoFile = File(MainActivity.storageDir, "${Constants.CUSTOM_CONTENT_DIR}/$fileName")
                                        mediaPlayer.setDataSource(videoFile.toString())
                                        mediaPlayer.setVolume(0f,0f)
                                        mediaPlayer.isLooping = true
                                        mediaPlayer.setOnPreparedListener {
                                            Log.d("CustomVideo", "MediaPlayer Set for $videoFile")
                                            it.start()
                                        }
                                        mediaPlayer.setOnCompletionListener {
                                            mediaPlayer.stop()
                                            Log.d("CustomVideo", "MediaPlayer stopped")
                                        }
                                        mediaPlayer.prepareAsync()
                                        for (i in 0..(mediaTime/1000)) {
                                            if (currentMedia != CURRENT_CUSTOM) {
                                                Log.d("CustomVideo", "Stopping Main Loop")
                                                break@mainLoop
                                            }
                                            delay(1000)
                                        }
                                        mediaPlayer.stop()
                                        mediaPlayer.reset()
                                    } catch (e: Exception) {
                                        Log.e("CustomLayout", "Media Player error in $fileName - $e")
                                        delay(mediaTime)
                                    }
                                }
                            }
                        }
                    }
                    Log.d("CustomLayout", "Out from the main loop")
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
                Log.e("Error releasing player", "$e")
            }

        }
        mediaPlayerList.clear()
        Log.d("CustomLayout", "$released media player stopped and released")
    }

    private fun setWeatherAndTimeLayout(wNtLayout: ConstraintLayout) {
        wNtLayout.removeAllViews()
        if(Constants.showWeather) {
            addInWeatherTimeLayout(Constants.weatherDataArray, wNtLayout, true)
        }
        if (Constants.showTime) {
            addInWeatherTimeLayout(Constants.dateTimeDataArray, wNtLayout, false)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addInWeatherTimeLayout(jsonArray: JSONArray, wNtLayout: ConstraintLayout, isWeatherElseTime : Boolean) {
        for(i in 0 until jsonArray.length()) {
            val timeText = TextView(ctx)
            val jsonObject = jsonArray[i] as JSONObject
            val x = jsonObject.get("x").toString().toInt()
            val y = jsonObject.get("y").toString().toInt()
            Log.d("X and Y", "$x and $y")
            val text = jsonObject.get("text").toString()
            if (text.contains("AM ") || text.contains("PM "))  {
                timeInAMPM = true
            } else {
                timeInAMPM = false
            }
            val textColor = getColorFromString(jsonObject.get("fill").toString())
            timeText.setTextColor(textColor)
       //     val layoutWidth = jsonObject.get("width").toString().toFloat()
       //     val layoutHeight = jsonObject.get("height").toString().toFloat()
            timeText.text = "---"
    //        timeText.scaleX = jsonObject.get("scaleX").toString().toFloat()
   //         timeText.scaleY = jsonObject.get("scaleY").toString().toFloat()
            timeText.setTextSize(jsonObject.get("fontSize").toString().toFloat() * Constants.weatherAndTimeFontSizeMultiplier)
            val layoutParams = ConstraintLayout.LayoutParams(
           //     (layoutWidth * wMulti).toInt() , (layoutHeight.toInt() * hMulti).toInt()
                ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                when(Constants.rotationAngel) {
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
                        topMargin = MainActivity.displayHeight - (y*hMulti).toInt()
                    }
                }
            }
            timeText.layoutParams = layoutParams
           //   timeText.setTextSize(10f)
            timeText.rotation = Constants.rotationAngel
            if(isWeatherElseTime) {
                val latLongString = jsonObject.get("link").toString()
                CoroutineScope(Dispatchers.Main).launch {
                    val temperature = getWeatherFromApiAsync(latLongString).await()
                    timeText.text = "$temperature°C"
                }
            } else {
                val timeZoneString  = jsonObject.get("link").toString()
                CoroutineScope(Dispatchers.Main).launch {
                    val time = getTimeFromAPIAsync(timeZoneString).await()
                    if (time > 0) {
                        allClocks.add(ClockObject(time, timeText))
                        if (!areClocksRunning) {
                            timeHandler.post(timeRunnable)
                            areClocksRunning = true
                        }
                    }
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
                    Log.e("ErrorWeatherShadow", "$e")
                }
            }
            wNtLayout.addView(timeText)
        }
    }

    private fun getColorFromString(str: String): Int {
        val index = str.indexOf("(")
        var color = 0
        if(index > 0) {
            var finalString = str.substring(index +1, str.length - 1)
            finalString.replace(" ", "").also { finalString = it }
            val colorsArray = finalString.split(",")
            color = Color.rgb(
                colorsArray[0].toInt(),
                colorsArray[1].toInt(),
                colorsArray[2].toInt()
            )
            Log.d("ColorString", finalString)
        }
        return color
    }

    private suspend fun getWeatherFromApiAsync(latLong: String) : Deferred<String> =
        coroutineScope {
            async(Dispatchers.IO) {
                try {
                    Log.d("weatherLink", latLong)
                    val latLongSplits = latLong.split(",")
                    Log.d("weatherLinkSize", "${latLongSplits.size}")
                    if (latLongSplits.size < 2) return@async "Err"
                    val latitude = latLongSplits[0]
                    val longitude = latLongSplits[1]
                    val apiURL = URL("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true")
                    val connection = apiURL.openConnection()
                    connection.connect()
                    val apiStream = connection.getInputStream()
                    val buff = ByteArray(4096)
                    val read = apiStream.read(buff)
                    val finalString = String(buff,0, read)
                    val weatherJson = JSONObject(finalString)
                    val currentWeatherJson = weatherJson.get("current_weather") as JSONObject
                    val temperature = currentWeatherJson.get("temperature")
                    Log.d("WeatherApiRes", finalString)
                    return@async temperature.toString()
                } catch (e: Exception) {
                    Log.e("WeatherAPIError", "$e")
                    return@async "Err"
                }
            }
        }

    private suspend fun getTimeFromAPIAsync(timeZone: String) : Deferred<Long> =
        coroutineScope {
            async(Dispatchers.IO) {
                try {
                    Log.d("TimeZoneApi", timeZone)
                    val cal = Calendar.getInstance()
                    if(timeZone.lowercase() == cal.timeZone.id.lowercase()) {
                        Log.d("TimeZone", "TimeZone same no api call")
                        return@async cal.timeInMillis
                    /*    val hour = cal.get(Calendar.HOUR_OF_DAY).toString()
                        var minute = cal.get(Calendar.MINUTE).toString()
                        if (minute.length == 1) {
                            minute = "0$minute"
                        }
                        timeToReturn += "$hour : $minute"  */
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
                        val timeToDisplayMill = utcTime + millOffset
                 /*       cal.timeInMillis = timeToDisplayMill
               //         Log.d("TimeZone", "$offsetHour : $offsetMinute,  ${cal.get(Calendar.HOUR_OF_DAY)} : ${cal.get(Calendar.MINUTE)}")
               //         Log.d("Timezone","utc :$utcTime, final offset :$millOffset")
                        val hour = cal.get(Calendar.HOUR_OF_DAY).toString()
                        var minute = cal.get(Calendar.MINUTE).toString()
                        if (minute.length == 1) {
                            minute = "0$minute"
                        }
                        timeToReturn += "$hour : $minute"  */
                        return@async timeToDisplayMill
                    }
                } catch (e: Exception) {
                    Log.e("TimeZoneAPI", "$e")
                    return@async -1;
                }
            }
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

    class ClockObject(var time: Long, val textView: TextView)

    companion object {
        const val PLAY_TYPE_IMAGE = 0
        const val PLAY_TYPE_VIDEO = 1
        const val PLAY_TYPE_LAYOUT = 2
        var playType : Int = PLAY_TYPE_IMAGE
        @SuppressLint("StaticFieldLeak")
        var playlistTextureView : TextureView? = null
    }
}