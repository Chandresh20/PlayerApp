package com.nento.player.app

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nento.player.app.api.ApiService
import com.nento.player.app.responses.UpdateResponse
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.*
import java.net.URL
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import com.nento.player.app.Constants.Companion.onSplashScreen
import com.nento.player.app.fragment.FragmentMedia
import io.sentry.Sentry
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.lang.Runnable
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var msgText : TextView
    private lateinit var countDownLayout: LinearLayout
    private var takeScreenshots = false
    private var keepDownloading = false
    private var imDownloading = false
    private lateinit var autoRestartHandler: Handler
    private lateinit var autoRestartRunnable: Runnable
    private var onPauseCalledOnce = false
    private var holdNewContent = false
    private lateinit var internetChangeReceiver : InternetChangeReceiver
    private lateinit var blinkHandler : Handler
    private var blinkRunnable3 : Runnable? = null
    private var blinkRunnable4 : Runnable? = null
    private var assignedContent = 0
    private lateinit var offlineIcon : ImageView
    private val blinkDuration = 500L

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences(Constants.PREFS_MAIN, MODE_PRIVATE)
        setContentView(R.layout.activity_main)
        window?.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
 //       Constants.orientationVertical = sharedPreferences.getBoolean(Constants.PREFS_IS_VERTICAL, false)
        Constants.rotationAngel = sharedPreferences.getFloat(Constants.PREFS_ROTATION_ANGLE, 0f)
        Log.d("OrientationAngle", "${Constants.rotationAngel}")
        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        mainViewModel.isIdHidden.value = sharedPreferences.getBoolean(Constants.PREFS_IS_ID_HIDDEN, false)
 //       startService(Intent(this, RestartService::class.java))
        msgText = findViewById(R.id.messageText)
        offlineIcon = findViewById(R.id.offlineIcon)
        countDownLayout = findViewById(R.id.countDownLayout)
        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(this))
        storageDir = applicationContext.getExternalFilesDir("Contents")!!
        cancelRestartAlarm()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onPauseCalledOnce = true
        }
  //      sendToSentry("Testing sentry")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getDeviceMemory()
        if (Build.VERSION.SDK_INT >= 30) {
            window.decorView.windowInsetsController
                ?.hide(
                    WindowInsets.Type.statusBars() or
                            WindowInsets.Type.navigationBars())
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
        blinkHandler = Handler(mainLooper)
        updateHandler2 = Handler(mainLooper) {
            val updates = it.obj as String
            msgText.text = "Content Loading $updates"
            true
        }
        messageHandler = Handler(mainLooper) {
            val msg = it.obj as String
            msgText.visibility = View.VISIBLE
            msgText.text = msg
            true
        }
    /*    rotationHandler = Handler(mainLooper) {
            val isVertical = it.obj as Boolean
            requestedOrientation = if (isVertical) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            true
        } */
        autoRestartHandler = Handler(mainLooper)
        autoRestartRunnable = kotlinx.coroutines.Runnable {
            throw Exception("AutoRestart")
        }
        internetChangeReceiver = InternetChangeReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val winManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val winMetrics = winManager.currentWindowMetrics
            val bounds = winMetrics.bounds
            displayWidth = bounds.width()
            displayHeight = bounds.height()
        } else {
            val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(0)
            val point = Point()
            display.getSize(point)
            displayWidth = point.x
            displayHeight = point.y
        }
        Log.d("DisplayResolution", "$displayWidth, $displayHeight")
        mainViewModel.isOffline.value = !internetChangeReceiver.isInternetAvailable(this)
        checkForUpdate2()
        checkPermission()
        var screenId = sharedPreferences.getString(Constants.PREFS_SCREEN_ID, "")
        if (screenId.isNullOrBlank()) {
            screenId = Utils.generateRandomId()
            sharedPreferences.edit().putString(Constants.PREFS_SCREEN_ID, screenId).apply()
        }
        Constants.screenID = screenId
        Constants.playerId = sharedPreferences.getString(Constants.PREFS_PLAYER_ID, "") ?: ""

        // check if screen is paired
        val isScreenPaired = sharedPreferences.getBoolean(Constants.PREFS_SCREEN_PAIRED, false)
        if (isScreenPaired) {
            mainViewModel.isScreenPaired.value = true
        }
        registerReceiver(internetChangeReceiver, IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"))

        // setSocket Here
        setSocket()
        Log.d("Listening", "screen_data")
        mSocket.on(Constants.SOCKET_SCREEN_DATA, screenSetupListener)
        mSocket.on(Constants.SOCKET_TEMPLATE_DATA, templateListener)
        mSocket.on(Constants.SOCKET_PLAYLIST_DATA, playlistListener)
        mSocket.on(Constants.SOCKET_CUSTOM_DATA, customLayoutListener)
        mSocket.on(Constants.SOCKET_CHECK_ONLINE_STATUS, amOnlineListener)
        mSocket.on(Constants.SOCKET_CLOSE_SCREEN_APP, closeListener)
        mSocket.on(Constants.SOCKET_UPGRADE_SCREEN_APP, upgradeListener)
        mSocket.on(Constants.SOCKET_RESET_SCREEN_APP, resetListener)
        mSocket.on(Constants.SOCKET_TAKE_SCREEN_SNAP_SHOT, screenShotListener)
        mSocket.on(Constants.SOCKET_CHECK_LAST_UPLOAD_DATA, lastUploadListener)
        mSocket.on(Constants.SOCKET_UPDATE_MEDIA_IN_SCREEN, mediaUpdateListener)
        mSocket.on(Constants.SOCKET_ROTATE_SCREEN, rotateScreenListener)
        mSocket.on(Constants.SOCKET_SHOW_HIDE_SCREEN_NUMBER, toggleScreenNumberListener)
        sendLastUpdate()
        loadAssignedContent()
        CoroutineScope(Dispatchers.Main).launch {
            takeScreenshots = true
            takeScreenShotAndSendAsync().await()
        }
        sendDeviceInfo()
   /*     if (!isServiceRunning(SocketService::class.java)) {
            startService(Intent(this, SocketService::class.java))
        } else {
            Log.d("SocketService", "Already running")
        }  */
        setHourRefresh()
        CoroutineScope(Dispatchers.Main).launch {
            keepSendHeartBeatAsync().await()
        }
        val updateTrigger = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                checkForUpdate2()
            }
        }
        registerReceiver(updateTrigger, IntentFilter(Constants.CHECK_UPDATE_BROADCAST))

        mainViewModel.isOffline.observe( this) { offline ->
            if (offline) {
                offlineIcon.visibility = View.VISIBLE
                animateOfflineIcon()
            } else {
                offlineIcon.visibility = View.GONE
                inanimateOfflineIcon()
                Log.d("SwitchToo", "::::::::::::::online")
            }
        }
    }

    private fun loadAssignedContent() {
        assignedContent = sharedPreferences.getInt(Constants.PREFS_CONTENT_ASSIGNED, 0)
        Log.d("AlreadyContent", assignedContent.toString())
        if (assignedContent != 0) {
            startTimerToLoadContent(assignedContent)
        } else {
            countDownLayout.visibility = View.GONE
        }
    }

    private val screenSetupListener = Emitter.Listener { args ->
        val jsonObject = args[0] as JSONObject
        Log.d("message", jsonObject.toString())
        Constants.playerId = jsonObject.get("_id").toString()
        sharedPreferences.edit().putString(
            Constants.PREFS_PLAYER_ID, Constants.playerId).apply()
        CoroutineScope(Dispatchers.Main).launch {
            mainViewModel.isScreenPaired.value = true
        }
        sharedPreferences.edit().putBoolean(Constants.PREFS_SCREEN_PAIRED, true).apply()
        sendDeviceInfo()
    }

    private fun sendDeviceInfo() {
        // send screen details
        val detailObject = JSONObject()
        detailObject.put("ScreenNo", Constants.screenID)
        detailObject.put("PlayerId", Constants.playerId)
        detailObject.put("TotalMemory", Constants.deviceMemory)
        detailObject.put("ScreenWidth", "$displayWidth")
        detailObject.put("ScreenHeight", "$displayHeight")
        detailObject.put("Android API", "${Build.VERSION.SDK_INT}")
        Log.d("SendingScreenInfo", "$detailObject")
        mSocket.emit(Constants.SOCKET_SCREEN_DATA, detailObject)
    }

    private val templateListener = Emitter.Listener { args ->
        keepDownloading= false
        messageHandler.obtainMessage(0, "New Content Available").sendToTarget()
        val jsonObject = args[0] as JSONObject
        Log.d("TemplateJSON", jsonObject.toString())
        val templateName = jsonObject.get("file_name")
        val url = jsonObject.get("image_url")
        val mediaId = jsonObject.get("media_id")
        Log.d("TemplateURL", url.toString())
        CoroutineScope(Dispatchers.Main).launch {
            while (holdNewContent) {
                delay(1000)
            }
            val downFile = downloadTemplateAsync(url.toString()).await()
            if (downFile != null) {
                val originalMap = BitmapFactory.decodeFile(downFile.toString())
                try {
                    val newOutput : OutputStream
                    downFile.delete()
                    downFile.createNewFile()
                    newOutput = downFile.outputStream()
                    originalMap.compress(Bitmap.CompressFormat.JPEG, 100, newOutput)
                    if (Constants.onSplashScreen) {
                        sendBroadcast(Intent(Constants.START_MEDIA_BROADCAST))
                    }
                    delay(1000)
                    assignedContent = Constants.CONTENT_ASSIGNED_TEMPLATE
                    sharedPreferences.edit().apply {
                        putInt(Constants.PREFS_CONTENT_ASSIGNED, assignedContent)
                        putString(Constants.PREFS_CONTENT_ID, mediaId.toString())
                    }.apply()
                    clearPlaylistDir()
                    deleteCustomDir()
                    val templateIntent = Intent(Constants.NEW_TEMPLATE_READY_BROADCAST)
                    sendBroadcast(templateIntent)
                    //save file name
                    sharedPreferences.edit().apply {
                        putString(Constants.PREFS_TEMPLATE_NAME, templateName.toString())
                    }.apply()
                } catch (e: Exception) {
                    messageHandler.obtainMessage(0, "Error loading template").sendToTarget()
                }
            } else {
                messageHandler.obtainMessage(0, "Error").sendToTarget()
            }
        }
    }

    private suspend fun downloadTemplateAsync(urlStr : String) : Deferred<File?> =
        coroutineScope {
            async(Dispatchers.IO) {
                try {
                    while (imDownloading) {
                        delay(100)
                    }
                    delay(5000)
                    imDownloading = true
                    val url = URL(urlStr)
                    val urlConnection = url.openConnection() as HttpsURLConnection
                    urlConnection.connect()
                    val responseCode = urlConnection.responseCode
                    Log.d("TemplateResponseCode", "$responseCode")
                    val inStream = urlConnection.inputStream
                    val writeFile = File(storageDir, Constants.TEMPLATE_NAME)
                    val outStream = writeFile.outputStream()
                    var totalWrite = 0f
                    val contentLength = urlConnection.contentLength
                    Log.d("starting", "template download")
                    updateHandler2.obtainMessage(0, "0% (1/1)").sendToTarget()
                    var loopCount = 0
                    var buff : ByteArray
                    keepDownloading = true
                    while (keepDownloading) {
                        loopCount += 1
                        buff = ByteArray(1024)
                        val read = inStream.read(buff)
                        if (totalWrite >= contentLength) break
                        if (read > 0) {
                            outStream.write(buff, 0, read)
                        }
                        totalWrite += read
                        if (loopCount > 400) {
                            loopCount = 0
                            val update = (totalWrite/contentLength * 100).toInt()
                            updateHandler2.obtainMessage(0, "$update% (1/1)").sendToTarget()
                            Log.d("Writing File", "template : $totalWrite")
                        }
                    }
                    inStream.close()
                    outStream.close()
                    imDownloading = false
                    val update = (totalWrite/contentLength * 100).toInt()
                    updateHandler2.obtainMessage(0, "$update% (1/1)").sendToTarget()
                    Log.d("Finished File", "template : $totalWrite")
                    delay(2000)
                    messageHandler.obtainMessage(0, "").sendToTarget()
                    return@async writeFile
                } catch (e: Exception) {
                    Log.e("TemplateError", "$e")
                    imDownloading = false
                    messageHandler.obtainMessage(0, "Error downloading Template").sendToTarget()
                    messageHandler.postDelayed( {
                        messageHandler.obtainMessage(0, "").sendToTarget()
                    }, 5000)
                    return@async null
                }
            }
        }

    private val playlistListener = Emitter.Listener { args ->
        keepDownloading = false
        messageHandler.obtainMessage(0, "New Content Available").sendToTarget()
        val jsonObject = args[0] as JSONObject
        val gson = Gson()
        val typeT = object : TypeToken<PlaylistObject>() {}
        val playlistObject = gson.fromJson<PlaylistObject>(jsonObject.toString(), typeT.type)
        Log.d("playlistObject", "$jsonObject")
        val mediaId = jsonObject.get("playlist_id")
        CoroutineScope(Dispatchers.Main).launch {
            while (holdNewContent) {
                delay(1000)
            }
            val success = downloadPlaylist2Async(playlistObject).await()
            if (!success) {
                messageHandler.obtainMessage(0, "Error").sendToTarget()
                return@launch
            }
            if (Constants.onSplashScreen) {
                sendBroadcast(Intent(Constants.START_MEDIA_BROADCAST))
            }
            currentPlaylist = playlistObject
            delay(1000)
            assignedContent = Constants.CONTENT_ASSIGNED_PLAYLIST
            sharedPreferences.edit().apply {
                putInt(Constants.PREFS_CONTENT_ASSIGNED, assignedContent)
                putString(Constants.PREFS_CONTENT_ID, mediaId.toString())
                }.apply()
            deleteCustomDir()
            val templateIntent = Intent(Constants.NEW_PLAYLIST_READY_BROADCAST)
            sendBroadcast(templateIntent)
        }
    }

    private suspend fun downloadPlaylist2Async(playlistObject: PlaylistObject) : Deferred<Boolean> =
        coroutineScope {
            async(Dispatchers.IO) {
                try {
                    while (imDownloading) {
                        delay(100)
                        Log.d("imDownloading", "true")
                    }
                    val downloadDir = File(storageDir, Constants.DOWNLOAD_CONTENT_DIR)
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs()
                    }
                    if (!playlistObject.playlist.isNullOrEmpty()) {
                        imDownloading = true
                        val totalCount = (playlistObject.playlist ?: emptyList()).size
                        var itemCount = 0
                        for (item in playlistObject.playlist!!) {
                            Log.d("Starting", item.mediaId ?: "NA")
                            if (item.sIncluded == false) {
                                Log.d("Skipping", "${item.mediaName}")
                                continue
                            }
                            val url = URL(item.mediaId)
                            val urlConnection = url.openConnection()
                            val inStream = urlConnection.getInputStream()
                            val writeFile = File(storageDir, "${Constants.DOWNLOAD_CONTENT_DIR}/${item.mediaName}")
                            val contentLength = urlConnection.contentLength
                            if (writeFile.exists()) {
                                writeFile.delete()
                            }
                            writeFile.createNewFile()
                            val outStream = writeFile.outputStream()
                            var totalWrite = 0f
                            Log.d("starting", "${item.playListId}")
                            itemCount += 1
                            updateHandler2.obtainMessage(0, "0% ($itemCount/$totalCount)").sendToTarget()
                            var loopCount = 0
                            var noReceive = 0
                            var buff : ByteArray?
                            keepDownloading = true
                            while (keepDownloading) {
                                loopCount += 1
                                buff = ByteArray(1024)
                                val read = inStream.read(buff)
                                if (totalWrite >= contentLength) break
                                if (read > 0) {
                                    outStream.write(buff, 0, read)
                                    noReceive = 0
                                } else {
                                    noReceive += 1
                                    if (noReceive > 50)
                                        keepDownloading = false
                                }
                                totalWrite += read
                                if (loopCount > 400) {
                                    loopCount = 0
                                    val update = (totalWrite/contentLength * 100).toInt()
                                    updateHandler2.obtainMessage(0, "$update% ($itemCount/$totalCount)").sendToTarget()
                                    Log.d("Writing File", "${item.mediaName} : $totalWrite")
                                }
                            }
                            if(!keepDownloading) {
                                inStream.close()
                                outStream.close()
                                throw IOException("Interrupt")
                            }
                            inStream.close()
                            outStream.close()
                            val update = (totalWrite/contentLength * 100).toInt()
                            updateHandler2.obtainMessage(0, "$update% ($itemCount/$totalCount)").sendToTarget()
                            Log.d("Finished File", "${item.mediaName} : $totalWrite")
                        }
                        // TODO ("move downloaded content to playlistDir
                        val playlistDir = File(storageDir, Constants.PLAYLIST_DIR_NAME)
                        if (!playlistDir.exists()) playlistDir.mkdirs()
                        messageHandler.obtainMessage(0, "Coping data").sendToTarget()
                        for (downFile in (downloadDir.listFiles() ?: emptyArray())) {
                            val newPlaylistFile = File(playlistDir, downFile.name)
                            downFile.copyRecursively(newPlaylistFile, true)
                        }
                        downloadDir.deleteRecursively()
                        // save playlist object as file
                        val pObjectFile = File(storageDir, Constants.PLAYLIST_FILE_NAME)
                        val oWriter = ObjectOutputStream(pObjectFile.outputStream())
                        oWriter.writeObject(playlistObject)
                        oWriter.flush()
                        oWriter.close()
                        imDownloading = false
                        messageHandler.obtainMessage(0, "").sendToTarget()
                        return@async true
                    } else {
                        Log.e("Playlist", "Empty")
                        return@async false
                    }
                } catch (e:Exception) {
                    imDownloading = false
                    messageHandler.obtainMessage(0, "Error").sendToTarget()
                    return@async false
                }
            }
        }

    private val customLayoutListener = Emitter.Listener { args ->
        Log.d("CustomResponse", "${args[0]}")
        messageHandler.obtainMessage(0, "New Content Available").sendToTarget()
        keepDownloading = false
        CoroutineScope(Dispatchers.Main).launch {
            while (holdNewContent) {
                delay(1000)
            }
            val jsonObject = args[0] as JSONObject
            Log.d("CustomLayout", jsonObject.toString())
            val id = jsonObject.get("_id")
            val customInfoFile = File(storageDir, Constants.CUSTOM_LAYOUT_JSON_NAME)
            if (customInfoFile.exists()) customInfoFile.deleteRecursively()
            customInfoFile.writeText(args[0].toString())
            val gson = Gson()
            val typeT = object : TypeToken<CustomLayoutObject>() { }
            val customObject = gson.fromJson<CustomLayoutObject>(jsonObject.toString(), typeT.type)
            downloadCustomContents2Async(customObject.imageUrl).await()
            if (Constants.onSplashScreen) {
                sendBroadcast(Intent(Constants.START_MEDIA_BROADCAST))
                delay(1000)
            }
            assignedContent = Constants.CONTENT_ASSIGNED_CUSTOM
            sharedPreferences.edit().apply {
                putInt(Constants.PREFS_CONTENT_ASSIGNED, assignedContent)
                putString(Constants.PREFS_CONTENT_ID, id.toString())
            }.apply()
            Log.d("CustomLayout", "Sending Broadcast")
            clearPlaylistDir()
            val customIntent = Intent(Constants.NEW_CUSTOM_LAYOUT_READY_BROADCAST)
            sendBroadcast(customIntent)
        }
    }

    private suspend fun downloadCustomContents2Async(IUrls: List<CustomLayoutObject.URLObject>?) =
        coroutineScope {
            async(Dispatchers.IO) {
                try {
                    while (imDownloading) {
                        delay(100)
                    }
                    imDownloading = true
                    val downloadDir = File(storageDir, Constants.DOWNLOAD_CONTENT_DIR)
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs()
                    }
                    val totalCount = IUrls?.size
                    var itemCount = 0
                    updateHandler2.obtainMessage(0, "0% ($itemCount/$totalCount)").sendToTarget()
                    for (urlInfo in (IUrls ?: emptyList())) {
                        val fileName = File(downloadDir, urlInfo.name ?: "NA")
                        if (fileName.exists()) {
                            fileName.delete()
                        }
                        fileName.createNewFile()
                        val url = URL(urlInfo.url)
                        val connection = url.openConnection()
                        val inputStream = connection.getInputStream()
                        val contentLength = connection.contentLength
                        val outStream = fileName.outputStream()
                        var buff : ByteArray
                        var loopCount = 0
                        var totalWrite = 0L
                        itemCount += 1
                        keepDownloading = true
                        while (keepDownloading) {
                            loopCount += 1
                            buff = ByteArray(1024)
                            val read = inputStream.read(buff)
                            if (read > 0) {
                                outStream.write(buff, 0, read)
                            }
                            totalWrite += read
                            if (loopCount > 400) {
                                loopCount = 0
                                val update = ((totalWrite * 100)/contentLength)
                                updateHandler2.obtainMessage(0, "$update% ($itemCount/$totalCount)").sendToTarget()
                                Log.d("Writing File", "${urlInfo.name} : $totalWrite")
                            }
                            if (totalWrite >= contentLength) break
                        }
                        inputStream.close()
                        outStream.close()
                        if (!keepDownloading) {
                            throw IOException("Interrupt")
                        }
                        updateHandler2.obtainMessage(0, "100% ($itemCount/$totalCount)").sendToTarget()
                        Log.d("Writing File", "${urlInfo.name} : $totalWrite")
                        val name = urlInfo.name ?: "NA"
                        if (name.contains(".jpg") || name.contains(".png") || name.contains(".jpeg") ||
                            name.contains(".gif")) {
                            compressImageFileAsync(fileName).await()
                        }
                    }
                    //TODO ("move files to customLayout")
                    val customDir = File(storageDir, Constants.CUSTOM_CONTENT_DIR)
                    if (!customDir.exists()) {
                        customDir.mkdirs()
                    }
                    messageHandler.obtainMessage(0, "Coping Files").sendToTarget()
                    for (downFile in (downloadDir.listFiles() ?: emptyArray())) {
                        val custFile = File(customDir, downFile.name ?: "NA")
                        downFile.copyRecursively(custFile, true)
                    }
                    downloadDir.deleteRecursively()
                    imDownloading = false
                    messageHandler.obtainMessage(0, "").sendToTarget()
                } catch (e: java.lang.Exception) {
                    imDownloading =false
                    Log.e("CustomError", "Downloading.. $e")
                    messageHandler.obtainMessage(0, "Error Loading Content, Please try again").sendToTarget()
                    messageHandler.postDelayed( {
                        messageHandler.obtainMessage(0, "").sendToTarget()
                    }, 5000)
                }
            }
        }

    private suspend fun compressImageFileAsync(file : File) =
        coroutineScope {
            async(Dispatchers.IO) {
                Log.d("compressing", file.toString())
                try {
                    val originalMap = BitmapFactory.decodeFile(file.toString())
                    val aspectRatio = originalMap.width.toFloat() / originalMap.height
                    val scaledBitmap =
                        Bitmap.createScaledBitmap(originalMap, displayWidth, (displayWidth / aspectRatio).toInt(), false)
                    file.delete()
                    file.createNewFile()
                    val outputStream = file.outputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                } catch (e: Exception) {
                    Log.e("compressing", "$file - failed $e")
                }
            }
        }

    private val closeListener = Emitter.Listener {
        Log.d("Socket", "CloseApp")
        finishAffinity()
    }

    private val upgradeListener = Emitter.Listener {
        Log.d("Socket", "UpgradeApp")
        checkForUpdate2()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private val resetListener = Emitter.Listener {
        resetApp(applicationContext)
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private val screenShotListener = Emitter.Listener {
        Log.d("Socket", "Screenshot")
        captureScreenNow()
    }

    private val lastUploadListener = Emitter.Listener {
        sendLastUpdate()
    }

    private fun sendLastUpdate() {
        val jsonRes = JSONObject()
        val type : String = when(sharedPreferences.getInt(Constants.PREFS_CONTENT_ASSIGNED, 0)) {
            Constants.CONTENT_ASSIGNED_TEMPLATE -> Constants.TYPE_TEMPLATE
            Constants.CONTENT_ASSIGNED_PLAYLIST -> Constants.TYPE_PLAYLIST
            Constants.CONTENT_ASSIGNED_CUSTOM -> Constants.TYPE_CUSTOM
            else -> "Unknown"
        }
        val id = sharedPreferences.getString(Constants.PREFS_CONTENT_ID, "None")
        jsonRes.put("type", type)
        jsonRes.put("id", id)
        jsonRes.put("screenNumber", Constants.screenID)
        mSocket.emit(Constants.SOCKET_CHECK_LAST_UPLOAD_DATA, jsonRes)
    }

    private val amOnlineListener = Emitter.Listener { args ->
        Log.d("ImOnLine", "Present sent")
        mSocket.emit(Constants.SOCKET_CHECK_ONLINE_STATUS, args[0].toString())
    }

    private val mediaUpdateListener = Emitter.Listener { args ->
        val msg = args[0] as JSONObject
        Log.d("MediaUpdate", "$msg")
        val fileName = msg.get("file_name")
        val imageURL = msg.get("image_url")
        val mediaId = msg.get("media_id")
        when(assignedContent) {
            Constants.CONTENT_ASSIGNED_TEMPLATE -> {
                val templateName = sharedPreferences.getString(Constants.PREFS_TEMPLATE_NAME, "")
                Log.d("MediaUpdate", "$templateName && $fileName")
                if (templateName == fileName) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val downFile = downloadTemplateAsync(imageURL.toString()).await()
                        if (downFile != null) {
                            val originalMap = BitmapFactory.decodeFile(downFile.toString())
                            try {
                                val newOutput: OutputStream
                                downFile.delete()
                                downFile.createNewFile()
                                newOutput = downFile.outputStream()
                                originalMap.compress(Bitmap.CompressFormat.JPEG, 100, newOutput)
                                if (onSplashScreen) {
                                    sendBroadcast(Intent(Constants.START_MEDIA_BROADCAST))
                                }
                                delay(1000)
                                assignedContent = Constants.CONTENT_ASSIGNED_TEMPLATE
                                sharedPreferences.edit().apply {
                                    putInt(Constants.PREFS_CONTENT_ASSIGNED, assignedContent)
                                    putString(Constants.PREFS_CONTENT_ID, mediaId.toString())
                                }.apply()
                                clearPlaylistDir()
                                deleteCustomDir()
                                val templateIntent = Intent(Constants.NEW_TEMPLATE_READY_BROADCAST)
                                sendBroadcast(templateIntent)
                                //save file name
                                sharedPreferences.edit().apply {
                                    putString(
                                        Constants.PREFS_TEMPLATE_NAME,
                                        templateName.toString()
                                    )
                                }.apply()
                            } catch (e: Exception) {
                                messageHandler.obtainMessage(0, "Error loading template")
                                    .sendToTarget()
                            }
                        } else {
                            messageHandler.obtainMessage(0, "Error").sendToTarget()
                        }
                    }
                }
            }
            Constants.CONTENT_ASSIGNED_PLAYLIST -> {
                val playListDir = File(storageDir, Constants.PLAYLIST_DIR_NAME)
                val files = playListDir.listFiles()
                for (file in files) {
                    Log.d("MediaUpdate", "${file.name} && $fileName")
                    if (file.name == fileName) {
                        Log.d("MediaUpdate", "Downloading image")
                        val downloadDir = File(storageDir, Constants.DOWNLOAD_CONTENT_DIR)
                        if (!downloadDir.exists()) downloadDir.mkdirs()
                        val newFile = File(downloadDir, fileName)
                        val inStream = URL(imageURL.toString()).openStream()
                        val outStream = newFile.outputStream()
                        var buff: ByteArray
                        while(true) {
                            buff = ByteArray(1024)
                            val read = inStream.read(buff)
                            if (read <= 0) {
                                break
                            }
                            outStream.write(buff, 0, read)
                        }
                        Log.d("MediaUpdate", "Finished $fileName")
                        Log.d("MediaUpdate", "Coping to PlaylistDir")
                        val newImageFile = File(playListDir, fileName)
                        newFile.copyRecursively(newImageFile, true)
                        Log.d("MediaUpdate", "Copied")
                        downloadDir.deleteRecursively()
                    }
                }
            }
        }
    }

    private val rotateScreenListener = Emitter.Listener { args ->
        val msg = args[0]
        Log.d("RotationUpdate", "$msg")
    /*    Constants.orientationVertical = msg.toString() == "true"
        sharedPreferences.edit().apply {
            putBoolean(Constants.PREFS_IS_VERTICAL, Constants.orientationVertical)
        }.apply()  */
        Constants.rotationAngel += 90f
        if (Constants.rotationAngel >= 360f) {
            Constants.rotationAngel = 0f
        }
        sharedPreferences.edit().apply {
            putFloat(Constants.PREFS_ROTATION_ANGLE, Constants.rotationAngel)
        }.apply()
        if (!onSplashScreen) {
            broadcastContent(assignedContent)
            Log.d("RotationUpdate", "Content Broadcast")
        }
   //     loadAssignedContent(false)
    }

    private val toggleScreenNumberListener = Emitter.Listener { args ->
        val hideScreenNumber = mainViewModel.isIdHidden.value ?: false
        mainViewModel.isIdHidden.postValue(!hideScreenNumber)
        sharedPreferences.edit().apply {
            putBoolean(Constants.PREFS_IS_ID_HIDDEN, !hideScreenNumber)
        }.apply()
    }

    private fun checkForUpdate2() {
        if (internetChangeReceiver.isInternetAvailable(this)) {
            messageHandler.obtainMessage(0, "Checking for update").sendToTarget()
            Log.d("Checking Update", "now")
            //      messageHandler.obtainMessage(0, "Checking Update").sendToTarget()
            val jsonObject = JSONObject()
            jsonObject.put("appVersion", Constants.APP_VERSION_CODE)
            jsonObject.put("appVersionName", Constants.APP_VERSION_NAME)
            Log.d("UpdateRequest", "$jsonObject")
            ApiService.apiService.checkForUpdate(jsonObject.toString(), "application/json")
                .enqueue(object : Callback<UpdateResponse> {
                    override fun onResponse(
                        call: Call<UpdateResponse>,
                        response: Response<UpdateResponse>
                    ) {
                        Log.d("UpdateResponse", "${response.body()?.data?.isUpdateAvailable}")
                        if (response.isSuccessful) {
                            if (response.body()?.data?.isUpdateAvailable == true) {
                                /*     startActivity(Intent(Intent.ACTION_VIEW,
                                         Uri.parse(response.body()?.data?.updateUrl)))  */
                                Log.d("updateUrl", "${response.body()?.data?.updateUrl}")
                                val updateURL = response.body()?.data?.updateUrl
                                CoroutineScope(Dispatchers.Main).launch {
                                    if (updateURL != null && updateURL.contains(".apk")) {
                                        //     messageHandler.obtainMessage(0, "Downloading Update").sendToTarget()
                                        downloadAPKAndInstall2Async(updateURL).await()
                                        //    (response.body()?.data?.newVersion) ?: 0).await()
                                    } else {
                                        //        messageHandler.obtainMessage(0, "Redirecting").sendToTarget()
                                        startActivity(Intent(Intent.ACTION_VIEW,
                                            Uri.parse(response.body()?.data?.updateUrl)))
                                    }
                                }
                            } else {
                                messageHandler.obtainMessage(0, "Latest Version already downloaded").sendToTarget()
                                updateHandler2.postDelayed( {
                                    messageHandler.obtainMessage(0, "").sendToTarget()
                                }, 3000)
                            }
                        }
                    }

                    override fun onFailure(call: Call<UpdateResponse>, t: Throwable) {
                        //            messageHandler.obtainMessage(0, "Failed to update").sendToTarget()
                        Log.e("UpdateError", "${t.message}")
                        messageHandler.obtainMessage(0, "Failed to get update").sendToTarget()
                        autoRestartHandler.postDelayed( {
                            messageHandler.obtainMessage(0, "").sendToTarget()
                        }, 5000)
                    }
                })
        } else {
            messageHandler.obtainMessage(0, "No Network").sendToTarget()
            updateHandler2.postDelayed( {
                messageHandler.obtainMessage(0, "").sendToTarget()
            }, 3000)
        }
    }

    private suspend fun downloadAPKAndInstall2Async(urlStr: String) =
        coroutineScope {
            async(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    downloadAndInstallUpdateOnQ(urlStr)
                } else {
                    downloadAndInstallUpdate(urlStr)
                }
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadAndInstallUpdateOnQ(urlStr: String) {
        try {
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, "updated.apk")
                put(MediaStore.Files.FileColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            val url = URL(urlStr)
            val httpConnection = url.openConnection()
            val inStrm = httpConnection.getInputStream()
            val contentLength = httpConnection.contentLength
            val outStrm = resolver.openOutputStream(uri!!)!!
            var write = 0
            var loop = 0
            while (write < contentLength) {
                loop += 1
                val buff = ByteArray(4096)
                val read = inStrm.read(buff)
                outStrm.write(buff, 0, read)
                write += read
                if (loop >= 200) {
                    loop = 0
                    val update = (write.toFloat() * 100 / contentLength).toInt()
                    messageHandler.obtainMessage(0, "Downloading updates: $update %").sendToTarget()
                }
                Log.d("writing", "$write")
            }
            Log.d("writing completed", "$write")
            messageHandler.obtainMessage(0, "").sendToTarget()
            val vIntent = Intent(Intent.ACTION_VIEW)
            vIntent.setDataAndType(uri, "application/vnd.android.package-archive")
            vIntent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivity(vIntent)
        } catch (e: java.lang.Exception)  {
            messageHandler.obtainMessage(0, "Error downloading update").sendToTarget()
            Log.d("ErrorUpdate", "$e")
            messageHandler.postDelayed( {
                messageHandler.obtainMessage(0, "").sendToTarget()
            }, 5000)
        }
    }

    private fun downloadAndInstallUpdate(urlStr: String) {
        try {
            val url = URL(urlStr)
            val httpConnection = url.openConnection()
            val inStrm = httpConnection.getInputStream()
            val contentLength = httpConnection.contentLength
            val outFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "update.apk")
            val outStrm = outFile.outputStream()
            var write = 0
            var loop = 0
            while (write < contentLength) {
                loop += 1
                val buff = ByteArray(4096)
                val read = inStrm.read(buff)
                outStrm.write(buff, 0, read)
                write += read
                if (loop >= 200) {
                    loop = 0
                    val update = (write.toFloat() * 100 / contentLength).toInt()
                    messageHandler.obtainMessage(0, "Downloading updates: $update %").sendToTarget()
                }
                Log.d("writing", "$write")
            }
            Log.d("writing completed", "$write")
            messageHandler.obtainMessage(0, "").sendToTarget()
            val vIntent = Intent(Intent.ACTION_VIEW)
            val contentUri = FileProvider.getUriForFile(this, this.packageName+".fileprovider", outFile)
            Log.d("ContentURI", "$contentUri")
            vIntent.setDataAndType(contentUri, "application/vnd.android.package-archive")
            vIntent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivity(vIntent)
            startActivity(vIntent)
        } catch (e: java.lang.Exception)  {
            messageHandler.obtainMessage(0, "Error getting updates").sendToTarget()
            Log.d("ErrorUpdate", "$e")
            messageHandler.postDelayed( {
                messageHandler.obtainMessage(0, "").sendToTarget()
            }, 5000)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun takeScreenShotAndSendAsync() =
        coroutineScope {
            async(Dispatchers.Main) {
                while (takeScreenshots) {
                    captureScreenNow()
                    delay(60000)
                }
            }
        }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun captureScreenNow() {
        val playlistVideView = FragmentMedia.playlistTextureView ?: return
        val view: View = window.decorView
        val saveFile = File(storageDir, "${Constants.playerId}.jpg")
        try {
            when(FragmentMedia.playType) {
                FragmentMedia.PLAY_TYPE_IMAGE -> {
                    Log.d("ScreenPLay", "Image")
                    val bMap1 = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bMap1)
                    view.draw(canvas)
                    val bMap = view.drawToBitmap(Bitmap.Config.ARGB_8888)
                    val aspectRation : Float = bMap.width.toFloat() / bMap.height
                    val reqWidth = 400
                    val scaledMap = Bitmap.createScaledBitmap(bMap, reqWidth, (reqWidth / aspectRation).toInt(), false)
                    val saveOutputStream = saveFile.outputStream()
                    scaledMap.compress(Bitmap.CompressFormat.JPEG, 100, saveOutputStream)
                    saveOutputStream.close()
                }
                FragmentMedia.PLAY_TYPE_VIDEO -> {
                    Log.d("ScreenPLay", "Video")
                    val bMap1 = Bitmap.createBitmap(playlistVideView.width,
                        playlistVideView.height, Bitmap.Config.ARGB_8888)
                 /*   val handlerThread = HandlerThread("PixelCopier")
                    handlerThread.start()
                    PixelCopy.request(playlistVideView, bMap1, {
                        if (it == PixelCopy.SUCCESS) {
                            Log.d("PixelCopy", "Success")
                            val aspectRation : Float = bMap1.width.toFloat() / bMap1.height
                            val reqWidth = 400
                            val scaledMap = Bitmap.createScaledBitmap(bMap1, reqWidth, (reqWidth / aspectRation).toInt(), false)
                            val saveOutputStream = saveFile.outputStream()
                            scaledMap.compress(Bitmap.CompressFormat.JPEG, 100, saveOutputStream)
                            saveOutputStream.close()
                        } else {
                            Log.e("PixelCopy", "Failure")
                        }
                    }, Handler(handlerThread.looper))  */
                }
                FragmentMedia.PLAY_TYPE_LAYOUT -> {
                    Log.d("ScreenPLay", "Layout")
                    val bMap1 = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bMap1)
                    view.draw(canvas)
                    val bMap = view.drawToBitmap(Bitmap.Config.ARGB_8888)
                    val aspectRation : Float = bMap.width.toFloat() / bMap.height
                    val reqWidth = 400
                    val scaledMap = Bitmap.createScaledBitmap(bMap, reqWidth, (reqWidth / aspectRation).toInt(), false)
                    val saveOutputStream = saveFile.outputStream()
                    scaledMap.compress(Bitmap.CompressFormat.JPEG, 100, saveOutputStream)
                    saveOutputStream.close()
                }
            }
            Log.d("ScreenShot", "Calling API")
            val readArray = saveFile.readBytes()
            val jObject = JSONObject()
            jObject.put("ss_image", String(readArray))
            val requestFile: RequestBody = saveFile.asRequestBody("image/jpg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("ss_image", saveFile.name, requestFile)
            ApiService.apiService.sendScreenShot(Constants.screenID, part)
                .enqueue(object : Callback<String> {
                    override fun onResponse(
                        call: Call<String>,
                        response: Response<String>
                    ) {
                        Log.d("ScreenShot", "Response: ${response.body()}")
                    }

                    override fun onFailure(call: Call<String>, t: Throwable) {
                        Log.e("ScreenShot", "Error, ${t.message}")
                    }

                })
            Log.d("ScreenShot", "Captured")
        } catch (e: Exception) {
            Log.e("ScreenShot", "Failed : $e")
        }
    }

    private fun startTimerToLoadContent(assignedContent : Int) {
        holdNewContent = true
        val secText = findViewById<TextView>(R.id.countDownSeconds)
        val contentHandler = Handler(mainLooper)
        var secRemaining = 15
        val contentRunnable = object : Runnable {
            override fun run() {
                if (secRemaining < 0) {
                    countDownLayout.visibility = View.GONE
                    broadcastContent(assignedContent)
                    holdNewContent = false
                    onSplashScreen = false
                    return
                }
                secText.text = secRemaining.toString()
                secRemaining -= 1
                contentHandler.postDelayed(this, 1000)
            }
        }
        contentHandler.post(contentRunnable)
    }

    private fun broadcastContent(assignedContent: Int) {
        when (assignedContent) {
            Constants.CONTENT_ASSIGNED_TEMPLATE -> {
                Log.d("Already", "template")
                CoroutineScope(Dispatchers.Main).launch {
                    delay(1000)
                    sendBroadcast(Intent(Constants.START_MEDIA_BROADCAST))
                    delay(500)
                    sendBroadcast(Intent(Constants.NEW_TEMPLATE_READY_BROADCAST))
                    messageHandler.obtainMessage(0, "").sendToTarget()
                }
            }
            Constants.CONTENT_ASSIGNED_PLAYLIST -> {
                Log.d("Already", "Playlist")
                try {
                    val playlistFile = File(storageDir, Constants.PLAYLIST_FILE_NAME)
                    val objectReader = ObjectInputStream(playlistFile.inputStream())
                    val playlistObj = objectReader.readObject() as PlaylistObject
                    objectReader.close()
                    currentPlaylist = playlistObj
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        sendBroadcast(Intent(Constants.START_MEDIA_BROADCAST))
                        delay(500)
                        sendBroadcast(Intent(Constants.NEW_PLAYLIST_READY_BROADCAST))
                        messageHandler.obtainMessage(0, "").sendToTarget()
                    }
                } catch (e:Exception) {
                    Log.e("PlaylistError", "$e")
                    messageHandler.obtainMessage(0, "Error").sendToTarget()
                }

            }
            Constants.CONTENT_ASSIGNED_CUSTOM -> {
                Log.d("Already", "Custom")
                CoroutineScope(Dispatchers.Main).launch {
                    delay(1000)
                    sendBroadcast(Intent(Constants.START_MEDIA_BROADCAST))
                    delay(500)
                    sendBroadcast(Intent(Constants.NEW_CUSTOM_LAYOUT_READY_BROADCAST))
                    messageHandler.obtainMessage(0, "").sendToTarget()
                }
            }
        }
    }

    private fun clearPlaylistDir() {
        val file = File(storageDir, Constants.PLAYLIST_DIR_NAME)
        if (file.exists()) {
            file.deleteRecursively()
        }
    }

    private fun deleteCustomDir() {
        val file = File(storageDir, Constants.CUSTOM_CONTENT_DIR)
        if (file.exists()) {
            file.deleteRecursively()
        }
    }

    private fun getDeviceMemory() {
        val actManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val bytes = memInfo.totalMem
        Constants.deviceMemory =  if (bytes < 1073741824) {
            val mb = bytes / 1048576
            "$mb MB"
        } else {
            val gb = (bytes.toFloat()*100 / 1073741824).toInt() / 100f
            "$gb GB"
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.REQUEST_INSTALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.REQUEST_INSTALL_PACKAGES), 100)
            Log.d("Permission", "Permission required")
        } else {
            Log.d("Permission", "Permission granted")
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onPause() {
        super.onPause()
        Log.d("OnPause", "calling")
        if (byPassMaximize) {
            Log.d("Maximized", "ByPassed")
            return
        }
        if (!onPauseCalledOnce) {
            onPauseCalledOnce = true
        } else {
            val restartTime = if (pauseForWifi) 120000 else 30000
            pauseForWifi = false
            val intent = Intent(this, TVActivity::class.java)
            try {
                intent.putExtra(Constants.INTENT_CRASH_RECOVERY, true)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                val pIntent = PendingIntent.getActivity(this,
                    0, intent, PendingIntent.FLAG_ONE_SHOT)
                val arm = getSystemService(ALARM_SERVICE) as AlarmManager
                arm.set(AlarmManager.RTC, System.currentTimeMillis() + restartTime, pIntent)
                Log.d("onPause", "Alarm Set")
            } catch (e: java.lang.Exception) {
                Log.e("UncaughtNew", "$e")
            }
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun cancelRestartAlarm() {
        val intent = Intent(applicationContext, TVActivity::class.java)
        val pIntent = PendingIntent.getActivity(applicationContext,
            0, intent, PendingIntent.FLAG_ONE_SHOT)
        val arm = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
        arm.cancel(pIntent)
        Log.d("Restart", "Canceled")
    }

    private suspend fun keepSendHeartBeatAsync() =
        coroutineScope {
            async(Dispatchers.IO) {
                while (true) {
                    val heatJson = JSONObject()
                    heatJson.put("screenNumber", Constants.screenID)
                    heatJson.put("onlineTime", "ONLINE")
                    heatJson.put("mac", Constants.screenID)
                    ApiService.apiService.sendHeartBeat(heatJson.toString(), "application/json")
                        .enqueue(object : Callback<String> {
                            override fun onResponse(
                                call: Call<String>,
                                response: Response<String>
                            ) {
                               Log.d("HeartBeat", "${response.body()}")
                            }

                            override fun onFailure(call: Call<String>, t: Throwable) {
                                Log.e("HeartBeat", "Failure")
                            }
                        })
                    delay(600000)
                }
            }
        }

    override fun onDestroy() {
        sendBroadcast(Intent(Constants.APP_DESTROYED_BROADCAST))
        Log.d("Destroyed", "sent Broadcast")
        autoRestartHandler.removeCallbacks(autoRestartRunnable)
        mSocket.disconnect()
        Process.killProcess(Process.myPid())
        exitProcess(2)
        super.onDestroy()
    }

    override fun onBackPressed() {
        mSocket.disconnect()
        finishAffinity()
    }

 /*   private fun isServiceRunning(serviceClass : Class<SocketService>) : Boolean {
        val aManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in aManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }  */

    private fun setHourRefresh() {
        autoRestartHandler.postDelayed(autoRestartRunnable, Constants.autoRestartTime)
    }

    private fun animateOfflineIcon() {
        blinkRunnable3 = kotlinx.coroutines.Runnable {
            offlineIcon.animate().alpha(0f).duration = blinkDuration
            blinkHandler.postDelayed(blinkRunnable4!!, blinkDuration)
        }
        blinkRunnable4 = kotlinx.coroutines.Runnable {
            offlineIcon.animate().alpha(1f).duration = blinkDuration
            blinkHandler.postDelayed(blinkRunnable3!!, blinkDuration)
        }
        blinkHandler.post(blinkRunnable3!!)
    }

    private fun inanimateOfflineIcon() {
        if (blinkRunnable3 != null && blinkRunnable4 != null) {
            blinkHandler.removeCallbacks(blinkRunnable3!!)
            blinkHandler.removeCallbacks(blinkRunnable4!!)
        }
    }


    companion object {
        lateinit var sharedPreferences : SharedPreferences
        lateinit var storageDir: File
        lateinit var messageHandler : Handler
        lateinit var currentPlaylist : PlaylistObject
        lateinit var mainViewModel: MainViewModel
        var displayWidth : Int = 0
        var displayHeight : Int = 0
        lateinit var rotationHandler : Handler
        lateinit var updateHandler2 : Handler
        lateinit var mSocket : Socket
        var byPassMaximize = false
        var pauseForWifi = false

        fun sendToSentry(msg: String) {
            Sentry.captureMessage(msg)
        }

        fun setSocket() {
            val clientBuilder: OkHttpClient.Builder =
                OkHttpClient.Builder().connectTimeout(0, TimeUnit.MILLISECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(0, TimeUnit.MILLISECONDS)
            val mOptions = IO.Options()
            mOptions.callFactory = clientBuilder.build()
            mSocket = IO.socket("${Constants.BASE_URL}?screenNo=${Constants.screenID}",
                mOptions)
            mSocket.connect()
            mSocket.emit(Constants.screenID, "Hello")
        }

        @SuppressLint("UnspecifiedImmutableFlag")
        fun resetApp(applicationContext : Context) {
            Log.d("Socket", "resetApp")
            sharedPreferences.edit().apply {
                putInt(Constants.PREFS_CONTENT_ASSIGNED, 0)
                putBoolean(Constants.PREFS_IS_RESET, true)
            }.apply()
            storageDir.deleteRecursively()
            val screenId = Utils.generateRandomId()
            CoroutineScope(Dispatchers.Main).launch {
                mainViewModel.isScreenPaired.value = true
            }
            sharedPreferences.edit().apply {
                putString(Constants.PREFS_SCREEN_ID, screenId)
                putBoolean(Constants.PREFS_SCREEN_PAIRED, false)
            }.apply()
            Constants.screenID = screenId
            Constants.playerId = sharedPreferences.getString(Constants.PREFS_PLAYER_ID, "") ?: ""
            val intent = Intent(applicationContext, TVActivity::class.java)
            intent.putExtra(Constants.INTENT_CRASH_RECOVERY, true)
            val pIntent = PendingIntent.getActivity(applicationContext,
                0, intent, PendingIntent.FLAG_ONE_SHOT)
            val arm = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
            arm.set(AlarmManager.RTC, System.currentTimeMillis() + 5000, pIntent)
            byPassMaximize = true
        }
    }
}