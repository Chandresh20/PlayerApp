package com.nento.player.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.hardware.display.DisplayManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity

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
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.nento.player.app.Constants.Companion.onSplashScreen
import com.nento.player.app.fragment.FragmentMedia
import io.sentry.Sentry
import io.sentry.SentryLevel
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import java.lang.Runnable
import java.net.NetworkInterface
import java.util.*
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

    //Wificonnection related variables (Added by Magesh)
    var mLocalBroadcastManager: LocalBroadcastManager? = null
  /*  val MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    val MY_UUID_REMOTE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    var bluetoothActivityResultLauncher: ActivityResultLauncher<Intent>? = null
    val NAME = "BluetoothDemo"
    val REMOTE_NAME = "RemoteBluetoothDemo"  */
    private var REQUIRED_PERMISSIONS: Array<String>? = null
 //   var rpl: ActivityResultLauncher<Array<String>>? = null
    var mBluetoothAdapter: BluetoothAdapter? = null
    var wifiConnectManager: WifiConnectManager? = null
    var mBounded = false
  //  var homekeypressed = false

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences(Constants.PREFS_MAIN, MODE_PRIVATE)
        setContentView(R.layout.activity_main)
        window?.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        Constants.rotationAngle = sharedPreferences.getFloat(Constants.PREFS_ROTATION_ANGLE, 0f)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        mainViewModel.isIdHidden.value = sharedPreferences.getBoolean(Constants.PREFS_IS_ID_HIDDEN, false)
        msgText = findViewById(R.id.messageText)
        offlineIcon = findViewById(R.id.offlineIcon)
        countDownLayout = findViewById(R.id.countDownLayout)
        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(this))
        storageDir = applicationContext.getExternalFilesDir("Contents")!!
  //      cancelRestartAlarm()
     /*   if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onPauseCalledOnce = true
        }  */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getDeviceMemory()
        getMacAddressRooted()
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
        mainViewModel.isOffline.value = !internetChangeReceiver.isInternetAvailable(this)
        checkForUpdate2()
        checkPermission()
        var screenId = sharedPreferences.getString(Constants.PREFS_SCREEN_ID, "")
        if (screenId.isNullOrBlank()) {
            screenId = Utils.generateRandomId2()
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
        mSocket?.on(Constants.SOCKET_SCREEN_DATA, screenSetupListener)
        mSocket?.on(Constants.SOCKET_TEMPLATE_DATA, templateListener)
        mSocket?.on(Constants.SOCKET_PLAYLIST_DATA, playlistListener)
        mSocket?.on(Constants.SOCKET_CUSTOM_DATA, customLayoutListener)
        mSocket?.on(Constants.SOCKET_CHECK_ONLINE_STATUS, amOnlineListener)
        mSocket?.on(Constants.SOCKET_CLOSE_SCREEN_APP, closeListener)
        mSocket?.on(Constants.SOCKET_UPGRADE_SCREEN_APP, upgradeListener)
        mSocket?.on(Constants.SOCKET_RESET_SCREEN_APP, resetListener)
        mSocket?.on(Constants.SOCKET_TAKE_SCREEN_SNAP_SHOT, screenShotListener)
        mSocket?.on(Constants.SOCKET_CHECK_LAST_UPLOAD_DATA, lastUploadListener)
        mSocket?.on(Constants.SOCKET_UPDATE_MEDIA_IN_SCREEN, mediaUpdateListener)
        mSocket?.on(Constants.SOCKET_ROTATE_SCREEN, rotateScreenListener)
        mSocket?.on(Constants.SOCKET_SHOW_HIDE_SCREEN_NUMBER, toggleScreenNumberListener)
        sendLastUpdate()
        loadAssignedContent()
        CoroutineScope(Dispatchers.Main).launch {
            takeScreenshots = true
            takeScreenShotAndSendAsync().await()
        }
        sendDeviceInfo()
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
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(applicationContext, "App is going to restart... ", Toast.LENGTH_LONG)
                .show()
            restartAppForEveryOneHour()
                  }, 3600000)
    //    }, 300000)  // only for testing

        //Wifi release permissions

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            REQUIRED_PERMISSIONS =
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            REQUIRED_PERMISSIONS =
                arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (ContextCompat.checkSelfPermission(this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            startwifiservice()
        }
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this)
        val mIntentFilter = IntentFilter()
        mIntentFilter.addAction(Constants.PLAYER_APP_CLOSE_BROADCAST)
        mLocalBroadcastManager!!.registerReceiver(mBroadcastReceiver, mIntentFilter)
    }

    // Codes by Magesh
    var mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == Constants.PLAYER_APP_CLOSE_BROADCAST) {
                finish()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startwifiservice()
        }
    }

    val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            Toast.makeText(this@MainActivity, "Service is disconnected", Toast.LENGTH_SHORT).show()
            mBounded = false
            wifiConnectManager = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Toast.makeText(this@MainActivity, "Service is connected", Toast.LENGTH_SHORT).show()
            mBounded = true
            val mLocalBinder: WifiConnectManager.LocalBinder = service as WifiConnectManager.LocalBinder
            wifiConnectManager = mLocalBinder.getServerInstance()
     //       val rd = Random()
            wifiConnectManager!!.setbluetoothdevicename(Constants.screenID)
        }
    }

    private fun startwifiservice() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.getAdapter()

        if (mBluetoothAdapter != null) {
            val mIntent = Intent(this, WifiConnectManager::class.java)
            bindService(mIntent, mConnection, BIND_AUTO_CREATE)
        } else {
            Toast.makeText(applicationContext, "Bluetooth device is not available . ", Toast.LENGTH_LONG).show()
        }
    }

/*    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.entries.all {
            it.value == true
        }
        permissions.entries.forEach {
            Log.e("LOG_TAG", "${it.key} = ${it.value}")
        }

        if (granted) {
            Toast.makeText(this, "Permission Granted started wifi service", Toast.LENGTH_SHORT).show()
            startwifiservice()
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS!!) {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }  */

    override fun onStop() {
        super.onStop()
        if (mBounded) {
            unbindService(mConnection)
            mBounded = false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.component = ComponentName("crazyboyfeng.justTvLauncher", "crazyboyfeng.justTvLauncher.LauncherActivity")
            try
            {
                startActivity(intent)
            }catch( e:ActivityNotFoundException){
                Toast.makeText(this,"Activity Not Found",Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // end of Magesh' codes


    @SuppressLint("UnspecifiedImmutableFlag")
    private fun restartAppForEveryOneHour () {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val mPendingIntentId = 10000
        val mPendingIntent = PendingIntent.getActivity(
            applicationContext,
            mPendingIntentId,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
        val mgr = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
        mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = mPendingIntent
        exitProcess(0)
    }

    private fun loadAssignedContent() {
        assignedContent = sharedPreferences.getInt(Constants.PREFS_CONTENT_ASSIGNED, 0)
        if (assignedContent != 0) {
            startTimerToLoadContent(assignedContent)
        } else {
            countDownLayout.visibility = View.GONE
        }
    }

    private val screenSetupListener = Emitter.Listener { args ->
        val jsonObject = args[0] as JSONObject
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
        mSocket?.emit(Constants.SOCKET_SCREEN_DATA, detailObject)
        Handler(mainLooper).postDelayed({
            checkForUpdate2()
        }, 20000)
    }

    private val templateListener = Emitter.Listener { args ->
        keepDownloading= false
        messageHandler.obtainMessage(0, "New Content Available").sendToTarget()
        val jsonObject = args[0] as JSONObject
        val templateName = jsonObject.get("file_name")
        val url = jsonObject.get("image_url")
        val mediaId = jsonObject.get("media_id")
        var isVertical = jsonObject.get("isVertical")
        isVertical = if (isVertical.toString().isBlank()) {
            false
        } else {
            isVertical.toString().toBoolean()
        }

        // added for weather and time
        val isWeather = try {
            jsonObject.get("isWether").toString()
        } catch (e: Exception) {
            ""
        }
        Constants.showWeather = if(isWeather.isBlank()) {
            false
        } else {
            isWeather.toBoolean()
        }
        if(Constants.showWeather) {
            val weatherJson = jsonObject.get("isWetherValue")
            try {
                Constants.weatherDataArray= JSONArray(weatherJson.toString())
            } catch (e: Exception) { }
        }

        val isDateTime = try {
            jsonObject.get("isDateTime").toString()
        } catch (e: Exception) {
            ""
        }
        Constants.showTime = if(isDateTime.isBlank()) {
            false
        } else {
            isDateTime.toBoolean()
        }
        if(Constants.showTime) {
            val dateTimeJson = jsonObject.get("isDateTimeValue")
            try {
                Constants.dateTimeDataArray = JSONArray(dateTimeJson.toString())
            }catch (e: Exception) { }
        }


        CoroutineScope(Dispatchers.Main).launch {
            while (holdNewContent) {
                delay(1000)
            }
            val downFile = downloadTemplateAsync(url.toString()).await()
            if (downFile != null) {
                try {
                    if (onSplashScreen) {
                        sendBroadcast(Intent(Constants.START_MEDIA_BROADCAST))
                    }
                    delay(1000)
                    assignedContent = Constants.CONTENT_ASSIGNED_TEMPLATE
                    sharedPreferences.edit().apply {
                        putInt(Constants.PREFS_CONTENT_ASSIGNED, assignedContent)
                        putString(Constants.PREFS_CONTENT_ID, mediaId.toString())
                        putBoolean(Constants.PREFS_CURRENT_TEMPLATE_VERTICAL, isVertical)
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
//                    val responseCode = urlConnection.responseCode
                    val inStream = urlConnection.inputStream
                    val writeFile = File(storageDir, Constants.TEMPLATE_NAME)
                    val outStream = writeFile.outputStream()
                    var totalWrite = 0f
                    val contentLength = urlConnection.contentLength
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
                        }
                    }
                    inStream.close()
                    outStream.close()
                    imDownloading = false
                    val update = (totalWrite/contentLength * 100).toInt()
                    updateHandler2.obtainMessage(0, "$update% (1/1)").sendToTarget()
                    delay(2000)
                    messageHandler.obtainMessage(0, "").sendToTarget()
                    return@async writeFile
                } catch (e: Exception) {
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

        //save json for investigation
        val tempFile = File(getExternalFilesDir("rw"), "playlistJson.txt")
        tempFile.writeText(jsonObject.toString())

        val gson = Gson()
        val typeT = object : TypeToken<PlaylistObject>() {}
        val playlistObject = gson.fromJson<PlaylistObject>(jsonObject.toString(), typeT.type)
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
            if (onSplashScreen) {
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
            Constants.showWeather = false
            Constants.showTime = false
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
                    }
                    val downloadDir = File(storageDir, Constants.DOWNLOAD_CONTENT_DIR)
                    var playlistDir = File(storageDir, Constants.PLAYLIST_DIR_NAME)
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs()
                    }
                    if (!playlistObject.playlist.isNullOrEmpty()) {
                        val totalCount = (playlistObject.playlist ?: emptyList()).size
                        var itemCount = 0
                        for (item in playlistObject.playlist!!) {
                            if ((item.shareData?.length ?: 0) > 10) {
                                // this is a layout item and needs to handle differently
//                                val gson = Gson()
//                                val typeT = object: TypeToken<List<CustomLayoutObject.LayoutInfo>>() {}.type
//                                val layoutList = gson.fromJson<List<CustomLayoutObject.LayoutInfo>>(JSONArray(item.shareData).toString(), typeT)
                                downloadLayoutForPlaylistAsync(item.imageUrl ?: emptyList()).await()
                                continue
                            }
                            // check if media is already available

                       /*     var existedFileLength = 0L
                            if((item.mediaName ?: "null").contains(".mp4") || (item.mediaName ?: "null").contains(".mkv")
                                || (item.mediaName ?: "null").contains(".mov") || (item.mediaName ?: "null").contains(".webm")
                                || (item.mediaName ?: "null").contains(".flv")) {
                                if (playlistDir.exists()) {
                                    val allFilesNames = playlistDir.list()
                                    if (allFilesNames != null && allFilesNames.contains(item.mediaName)) {
                                        Log.d("CheckPLaylisr", "${item.mediaName} already available")
                                        existedFileLength = File(playlistDir, item.mediaName!!).length()
                                    }
                                }
                            }  */
                            val existedFileLength = checkIfVideoAlreadyExists(item.mediaName ?: "null", playlistDir)

                            if (item.sIncluded == false) {
                                continue
                            }
                            val url = URL(item.mediaId)
                            val urlConnection = url.openConnection()
                            val contentLength = urlConnection.contentLength
                            if ((contentLength - existedFileLength) < 100) {
                                continue
                            }
                            val inStream = urlConnection.getInputStream()
                            val writeFile = File(storageDir, "${Constants.DOWNLOAD_CONTENT_DIR}/${item.mediaName}")
                            if (writeFile.exists()) {
                                writeFile.delete()
                            }
                            writeFile.createNewFile()
                            val outStream = writeFile.outputStream()
                            var totalWrite = 0f
                            itemCount += 1
                            updateHandler2.obtainMessage(0, "0% ($itemCount/$totalCount)").sendToTarget()
                            var loopCount = 0
                            var noReceive = 0
                            var buff : ByteArray?
                            imDownloading = true
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
                                }
                            }
                            if(!keepDownloading) {
                                if ((contentLength - totalWrite) > 100) {
                                    inStream.close()
                                    outStream.close()
                                    throw IOException("Interrupt")
                                }
                            }
                            inStream.close()
                            outStream.close()
                            val update = (totalWrite/contentLength * 100).toInt()
                            updateHandler2.obtainMessage(0, "$update% ($itemCount/$totalCount)").sendToTarget()
                        }

                        playlistDir = File(storageDir, Constants.PLAYLIST_DIR_NAME)
                        if (!playlistDir.exists()) playlistDir.mkdirs()
                        messageHandler.obtainMessage(0, "Copying data").sendToTarget()
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
        messageHandler.obtainMessage(0, "New Content Available").sendToTarget()
        keepDownloading = false
        CoroutineScope(Dispatchers.Main).launch {
            while (holdNewContent) {
                delay(1000)
            }
            val jsonObject = args[0] as JSONObject
            val id = jsonObject.get("_id")

            // added for weather and time
            val isWeather: String = try  {
                jsonObject.get("isWether").toString()
            } catch(e: Exception) {
                ""
            }
            Constants.showWeather = if(isWeather.isBlank()) {
                false
            } else {
                isWeather.toBoolean()
            }
            if(Constants.showWeather) {
                val weatherJson = jsonObject.get("isWetherValue")
                try {
                    Constants.weatherDataArray= JSONArray(weatherJson.toString())
                } catch (e: Exception) {
                }
            }

            val isDateTime = try {
                jsonObject.get("isDateTime").toString()
            } catch (e: Exception) {
                ""
            }
            Constants.showTime = if(isDateTime.isBlank()) {
                false
            } else {
                isDateTime.toBoolean()
            }
            if(Constants.showTime) {
                val dateTimeJson = jsonObject.get("isDateTimeValue")
                try {
                    Constants.dateTimeDataArray = JSONArray(dateTimeJson.toString())
                }catch (e: Exception) {
                }
            }

            val customInfoFile = File(storageDir, Constants.CUSTOM_LAYOUT_JSON_NAME)
            if (customInfoFile.exists()) customInfoFile.deleteRecursively()
            customInfoFile.writeText(args[0].toString())
            val gson = Gson()
            val typeT = object : TypeToken<CustomLayoutObject>() { }
            val customObject = gson.fromJson<CustomLayoutObject>(jsonObject.toString(), typeT.type)
            downloadCustomContents2Async(customObject.imageUrl).await()
            if (onSplashScreen) {
                sendBroadcast(Intent(Constants.START_MEDIA_BROADCAST))
                delay(1000)
            }
            assignedContent = Constants.CONTENT_ASSIGNED_CUSTOM
            sharedPreferences.edit().apply {
                putInt(Constants.PREFS_CONTENT_ASSIGNED, assignedContent)
                putString(Constants.PREFS_CONTENT_ID, id.toString())
            }.apply()
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
                    val customDir = File(storageDir, Constants.CUSTOM_CONTENT_DIR)
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs()
                    }
                    val totalCount = IUrls?.size
                    var itemCount = 0
                    updateHandler2.obtainMessage(0, "0% ($itemCount/$totalCount)").sendToTarget()
                    for (urlInfo in (IUrls ?: emptyList())) {
                        var alreadyAvailableContentSize = 0L
                        if (customDir.exists()) {
                            if((urlInfo.name ?: "na").contains(".mp4") || (urlInfo.name ?: "na").contains(".webm") ||
                                (urlInfo.name ?: "na").contains(".mkv") || (urlInfo.name ?: "na").contains(".mov")) {
                                val customContentList = customDir.list()
                                if (customContentList != null && customContentList.contains(urlInfo.name)) {
                                    alreadyAvailableContentSize = File(customDir, urlInfo.name!!).length()
                                }
                            }
                        }
                        val url = URL(urlInfo.url)
                        val connection = url.openConnection()
                        val contentLength = connection.contentLength
                        if ((contentLength - alreadyAvailableContentSize) < 100) {
                            continue
                        }
                        val fileName = File(downloadDir, urlInfo.name ?: "NA")
                        if (fileName.exists()) {
                            fileName.delete()
                        }
                        fileName.createNewFile()
                        val inputStream = connection.getInputStream()
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
                            }
                            if (totalWrite >= contentLength) break
                        }
                        inputStream.close()
                        outStream.close()
                        if (!keepDownloading) {
                            throw IOException("Interrupt")
                        }
                        updateHandler2.obtainMessage(0, "100% ($itemCount/$totalCount)").sendToTarget()
                    }
                    //TODO ("move files to customLayout")
                    if (!customDir.exists()) {
                        customDir.mkdirs()
                    }
                    messageHandler.obtainMessage(0, "Copying Files").sendToTarget()
                    for (downFile in (downloadDir.listFiles() ?: emptyArray())) {
                        val custFile = File(customDir, downFile.name ?: "NA")
                        downFile.copyRecursively(custFile, true)
                    }
                    downloadDir.deleteRecursively()
                    imDownloading = false
                    messageHandler.obtainMessage(0, "").sendToTarget()
                } catch (e: java.lang.Exception) {
                    imDownloading =false
                    messageHandler.obtainMessage(0, "Error Loading Content, Please try again").sendToTarget()
                    messageHandler.postDelayed( {
                        messageHandler.obtainMessage(0, "").sendToTarget()
                    }, 5000)
                }
            }
        }

    private suspend fun downloadLayoutForPlaylistAsync(imageUrls : List<PlaylistObject.ImageUrlObj>) =
        coroutineScope {
            async(Dispatchers.IO) {
                try {
                    imDownloading = true
                    val downloadDir = File(storageDir, Constants.DOWNLOAD_CONTENT_DIR)
                    val playlistDir = File(storageDir, Constants.PLAYLIST_DIR_NAME)
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs()
                    }
                    val totalCount = imageUrls.size
                    var itemCount = 0
                    for(imageUrl in imageUrls) {
                        itemCount++
                        val url = URL(imageUrl.url)
                        val connection = url.openConnection()
                        val contentLength = connection.contentLength

                        // check if video already exists
                        val availableLength = checkIfVideoAlreadyExists(imageUrl.name ?: "null", playlistDir)

                        if ((contentLength - availableLength) < 100) {
                            continue
                        }

                        val inStream = connection.getInputStream()
                        val outFile = File(downloadDir, imageUrl.name ?: "null")
                        if(outFile.exists()) outFile.delete()
                        outFile.createNewFile()
                        val outStream = outFile.outputStream()
                        var buff : ByteArray
                        var loopCount = 0
                        var totalWrite = 0f
                        keepDownloading = true
                        var noReceive = 0
                        updateHandler2.obtainMessage(0, "0% ($itemCount/$totalCount)").sendToTarget()


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
                            }
                        }
                        if(!keepDownloading) {
                            if ((contentLength - totalWrite) > 100) {
                                inStream.close()
                                outStream.close()
                                throw IOException("Interrupt")
                            }
                        }
                        inStream.close()
                        outStream.close()
                        val update = (totalWrite/contentLength * 100).toInt()
                        updateHandler2.obtainMessage(0, "$update% ($itemCount/$totalCount)").sendToTarget()
                    }
                    messageHandler.obtainMessage(0, "Copying data").sendToTarget()
                    for (downFile in (downloadDir.listFiles() ?: emptyArray())) {
                        val newPlaylistFile = File(playlistDir, downFile.name)
                        downFile.copyRecursively(newPlaylistFile, true)
                    }
                    imDownloading = false
                } catch (e: Exception) {
                    imDownloading = false
                    messageHandler.obtainMessage(0, "Error").sendToTarget()
                }
            }
        }

 /*   private suspend fun compressImageFileAsync(file : File) =
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
        }  */

    private val closeListener = Emitter.Listener {
        finishAffinity()
    }

    private val upgradeListener = Emitter.Listener {
        checkForUpdate2()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private val resetListener = Emitter.Listener {
        resetApp(applicationContext)
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private val screenShotListener = Emitter.Listener {
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
        mSocket?.emit(Constants.SOCKET_CHECK_LAST_UPLOAD_DATA, jsonRes)
    }

    private val amOnlineListener = Emitter.Listener { args ->
        mSocket?.emit(Constants.SOCKET_CHECK_ONLINE_STATUS, args[0].toString())
    }

    private val mediaUpdateListener = Emitter.Listener { args ->
        val msg = args[0] as JSONObject
        val fileName = msg.get("file_name")
        val imageURL = msg.get("image_url")
        val mediaId = msg.get("media_id")
        var isVertical = msg.get("isVertical")
        isVertical = if (isVertical.toString().isBlank()) {
            false
        } else {
            isVertical.toString().toBoolean()
        }
        when(assignedContent) {
            Constants.CONTENT_ASSIGNED_TEMPLATE -> {
                val templateName = sharedPreferences.getString(Constants.PREFS_TEMPLATE_NAME, "")
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
                                    putBoolean(Constants.PREFS_CURRENT_TEMPLATE_VERTICAL, isVertical)
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
                for (file in (files ?: emptyArray())) {
                    if (file.name == fileName) {
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
                        val newImageFile = File(playListDir, fileName)
                        newFile.copyRecursively(newImageFile, true)
                        downloadDir.deleteRecursively()
                    }
                }
            }
        }
    }

    private val rotateScreenListener = Emitter.Listener { args ->
     /*   if (Constants.verticalLayout) {
            messageHandler.obtainMessage(0,
                "Manual rotation doesn't support this layout, please change the layout").sendToTarget()
            messageHandler.postDelayed({
                messageHandler.obtainMessage(0, "").sendToTarget()
            }, 10000)
            return@Listener
        }  */
//        val msg = args[0]
        Constants.rotationAngle += 90f
        if (Constants.rotationAngle >= 360f) {
            Constants.rotationAngle = 0f
        }
        sharedPreferences.edit().apply {
            putFloat(Constants.PREFS_ROTATION_ANGLE, Constants.rotationAngle)
        }.apply()
        if (!onSplashScreen) {
            broadcastContent(assignedContent)
        }
    }

    private val toggleScreenNumberListener = Emitter.Listener {
        val hideScreenNumber = mainViewModel.isIdHidden.value ?: false
        mainViewModel.isIdHidden.postValue(!hideScreenNumber)
        sharedPreferences.edit().apply {
            putBoolean(Constants.PREFS_IS_ID_HIDDEN, !hideScreenNumber)
        }.apply()
    }

    private fun checkForUpdate2() {
        if (internetChangeReceiver.isInternetAvailable(this)) {
            messageHandler.obtainMessage(0, "Checking for update").sendToTarget()
            //      messageHandler.obtainMessage(0, "Checking Update").sendToTarget()
            CoroutineScope(Dispatchers.Main).launch {
                val jsonObject = JSONObject()
                jsonObject.put("appVersion", Constants.APP_VERSION_CODE)
                jsonObject.put("appVersionName", Constants.APP_PLAYSTORE)
                // new parameters for app version in dashboard
                jsonObject.put("appCurrentVersionName", Constants.APP_VERSION_NAME)
                jsonObject.put("screenNo", Constants.screenID)
                jsonObject.put("MAC", "-")
                jsonObject.put("RAM", Constants.deviceMemory)
                val myPublicIp = getMyPublicIpAsync().await()
                jsonObject.put("ipAddress", myPublicIp)
                jsonObject.put("orientation", Constants.rotationAngle)
                jsonObject.put("AndroidVersion", Build.VERSION.SDK_INT)
                ApiService.apiService.checkForUpdate(jsonObject.toString(), "application/json")
                    .enqueue(object : Callback<UpdateResponse> {
                        override fun onResponse(
                            call: Call<UpdateResponse>,
                            response: Response<UpdateResponse>
                        ) {
                            if (response.isSuccessful) {
                                if (response.body()?.data?.isUpdateAvailable == true) {
                                    /*     startActivity(Intent(Intent.ACTION_VIEW,
                                             Uri.parse(response.body()?.data?.updateUrl)))  */
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
                            } else {
                                messageHandler.obtainMessage(0, "No Update response").sendToTarget()
                                updateHandler2.postDelayed( {
                                    messageHandler.obtainMessage(0, "").sendToTarget()
                                }, 3000)
                            }
                        }

                        override fun onFailure(call: Call<UpdateResponse>, t: Throwable) {
                            //            messageHandler.obtainMessage(0, "Failed to update").sendToTarget()
                            messageHandler.obtainMessage(0, "Failed to get update").sendToTarget()
                            autoRestartHandler.postDelayed( {
                                messageHandler.obtainMessage(0, "").sendToTarget()
                            }, 5000)
                        }
                    })
            }
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
            }
            messageHandler.obtainMessage(0, "").sendToTarget()
            val vIntent = Intent(Intent.ACTION_VIEW)
            vIntent.setDataAndType(uri, "application/vnd.android.package-archive")
            vIntent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivity(vIntent)
        } catch (e: java.lang.Exception)  {
            messageHandler.obtainMessage(0, "Error downloading update").sendToTarget()
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
            }
            messageHandler.obtainMessage(0, "").sendToTarget()
            val vIntent = Intent(Intent.ACTION_VIEW)
            val contentUri = FileProvider.getUriForFile(this, this.packageName+".fileprovider", outFile)
            vIntent.setDataAndType(contentUri, "application/vnd.android.package-archive")
            vIntent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivity(vIntent)
            startActivity(vIntent)
        } catch (e: java.lang.Exception)  {
            messageHandler.obtainMessage(0, "Error getting updates").sendToTarget()
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
        val view: View = window.decorView
        val saveFile = File(storageDir, "${Constants.playerId}.jpg")
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getScreenShotFromView(view, this@MainActivity) { bMap ->
                    val aspectRation : Float = bMap.width.toFloat() / bMap.height
                    val reqWidth = 400
                    val scaledMap = Bitmap.createScaledBitmap(bMap, reqWidth, (reqWidth / aspectRation).toInt(), false)
                    val saveOutputStream = saveFile.outputStream()
                    scaledMap.compress(Bitmap.CompressFormat.JPEG, 100, saveOutputStream)
                    saveOutputStream.close()
                }
            } else {
                when (FragmentMedia.playType) {
                    FragmentMedia.PLAY_TYPE_IMAGE -> {
                        val bMap1 =
                            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bMap1)
                        view.draw(canvas)
                        val bMap = view.drawToBitmap(Bitmap.Config.ARGB_8888)
                        val aspectRation: Float = bMap.width.toFloat() / bMap.height
                        val reqWidth = 400
                        val scaledMap = Bitmap.createScaledBitmap(
                            bMap,
                            reqWidth,
                            (reqWidth / aspectRation).toInt(),
                            false
                        )
                        val saveOutputStream = saveFile.outputStream()
                        scaledMap.compress(Bitmap.CompressFormat.JPEG, 100, saveOutputStream)
                        saveOutputStream.close()
                    }
                    FragmentMedia.PLAY_TYPE_VIDEO -> {
                        val bMap = FragmentMedia.playlistTextureView?.bitmap
                        if (bMap != null) {
                            val matrix = Matrix()
                            matrix.postRotate(Constants.rotationAngle)
                            val aspectRation: Float = bMap.width.toFloat() / bMap.height
                            val reqWidth = 400
                            val scaledMap = Bitmap.createScaledBitmap(
                                bMap,
                                reqWidth,
                                (reqWidth / aspectRation).toInt(),
                                false
                            )
                            val rotatedMap = Bitmap.createBitmap(
                                scaledMap,
                                0,
                                0,
                                reqWidth,
                                (reqWidth / aspectRation).toInt(),
                                matrix,
                                true
                            )
                            val saveOutputStream = saveFile.outputStream()
                            rotatedMap.compress(Bitmap.CompressFormat.JPEG, 100, saveOutputStream)
                            saveOutputStream.close()
                        }
                    }
                    FragmentMedia.PLAY_TYPE_LAYOUT -> {
                        val bMap1 =
                            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bMap1)
                        view.draw(canvas)
                        val bMap = view.drawToBitmap(Bitmap.Config.ARGB_8888)
                        val aspectRation: Float = bMap.width.toFloat() / bMap.height
                        val reqWidth = 400
                        val scaledMap = Bitmap.createScaledBitmap(
                            bMap,
                            reqWidth,
                            (reqWidth / aspectRation).toInt(),
                            false
                        )
                        val saveOutputStream = saveFile.outputStream()
                        scaledMap.compress(Bitmap.CompressFormat.JPEG, 100, saveOutputStream)
                        saveOutputStream.close()
                    }
                }
            }
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
                        Log.d("ScreenShotResponse", "$response")
                    }

                    override fun onFailure(call: Call<String>, t: Throwable) {
                        Log.e("ScreenShotResponse", "${t.message}")
                    }

                })
        } catch (e: Exception) {
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
                CoroutineScope(Dispatchers.Main).launch {
                    delay(1000)
                    sendBroadcast(Intent(Constants.START_MEDIA_BROADCAST))
                    delay(500)
                    sendBroadcast(Intent(Constants.NEW_TEMPLATE_READY_BROADCAST))
                    messageHandler.obtainMessage(0, "").sendToTarget()
                }
            }
            Constants.CONTENT_ASSIGNED_PLAYLIST -> {
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
                    messageHandler.obtainMessage(0, "Error").sendToTarget()
                }

            }
            Constants.CONTENT_ASSIGNED_CUSTOM -> {
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

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.REQUEST_INSTALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.REQUEST_INSTALL_PACKAGES), 100)
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onPause() {
        super.onPause()
        if (byPassMaximize) {
            return
        }
        if (!onPauseCalledOnce) {
            onPauseCalledOnce = true
        } else {
    //        mSocket?.disconnect()
   //         Log.d("onPause", "Socket Disconnected")
            val restartTime = if (pauseForWifi) 240000 else 30000
//            val restartTime = if (pauseForWifi) 240000 else 10000  // only for testing
            pauseForWifi = false
//            val intent = Intent(this, TVActivity::class.java)
            val intent = Intent(this, MainActivity::class.java)  // changed after splash screen flashing reported
            try {
                intent.putExtra(Constants.INTENT_CRASH_RECOVERY, true)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                val pIntent = PendingIntent.getActivity(this,
                    0, intent, PendingIntent.FLAG_ONE_SHOT)
                val arm = getSystemService(ALARM_SERVICE) as AlarmManager
                arm.set(AlarmManager.RTC, System.currentTimeMillis() + restartTime, pIntent)
            } catch (e: java.lang.Exception) {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cancelRestartAlarm()
     /*   if (mSocket != null) {
            mSocket?.connect()
            Log.d("mSocket","Socket Reconnected")
        } */
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun cancelRestartAlarm() {
        val intent = Intent(applicationContext, TVActivity::class.java)
        val pIntent = PendingIntent.getActivity(applicationContext,
            0, intent, PendingIntent.FLAG_IMMUTABLE)
        val arm = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
        arm.cancel(pIntent)
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
                            }

                            override fun onFailure(call: Call<String>, t: Throwable) {
                            }
                        })
                    delay(600000)
                }
            }
        }

    override fun onDestroy() {
        sendBroadcast(Intent(Constants.APP_DESTROYED_BROADCAST))
        autoRestartHandler.removeCallbacks(autoRestartRunnable)
        mSocket?.disconnect()
        Process.killProcess(Process.myPid())
        exitProcess(2)
        super.onDestroy()
    }

    override fun onBackPressed() {
        mSocket?.disconnect()
        finishAffinity()
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

        lateinit var updateHandler2 : Handler
        var mSocket : Socket? = null
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
            mSocket?.connect()
            mSocket?.emit(Constants.screenID, "Hello")
        }

        @SuppressLint("UnspecifiedImmutableFlag")
        fun resetApp(applicationContext : Context) {
            sharedPreferences.edit().apply {
                putInt(Constants.PREFS_CONTENT_ASSIGNED, 0)
                putBoolean(Constants.PREFS_IS_RESET, true)
            }.apply()
            storageDir.deleteRecursively()
            val screenId = Utils.generateRandomId2()
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

    private fun getMacAddressRooted() {
        // turn wifi on if off (for lower than android 10)
     //   val wifiManager = applicationCon
        //   text.getSystemService(WIFI_SERVICE) as WifiManager
    //    wifiManager.isWifiEnabled = true
        val pathToDir = "/sys/class/net/"
        val netFaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (netface in netFaces) {
            if (netface.name.lowercase().contains("wlan0")) {
                try {
                    val addressFile = File(pathToDir, "${netface.name}/address")
                    mainViewModel.macAddress.value = addressFile.readText()
                    Log.d("MacAddressRooted", "Marking this as rooted device")
                    sharedPreferences.edit().putBoolean(
                        Constants.PREFS_DEVICE_ROOTED, true).apply()
                } catch (e:Exception) {
                    Log.e("MacAddressRooted", "$e")
                    Sentry.captureMessage("MacAddress : $e", SentryLevel.WARNING)
                    // mark this device as non-rooted if permission denied to access file
                    if (e.toString().contains("Permission denied")) {
                        Log.d("MacAddressRooted", "Marking this as non rooted device")
                        sharedPreferences.edit().putBoolean(
                            Constants.PREFS_DEVICE_ROOTED, false
                        ).apply()
                    }
                }
            }
        }
    }


    private fun getScreenShotFromView(view: View, activity: Activity, callback: (Bitmap) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.window?.let { window ->
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val locationOfViewInWindow = IntArray(2)
                view.getLocationInWindow(locationOfViewInWindow)
                try {
                        PixelCopy.request(
                            window,
                            Rect(
                                locationOfViewInWindow[0],
                                locationOfViewInWindow[1],
                                locationOfViewInWindow[0] + view.width,
                                locationOfViewInWindow[1] + view.height
                            ), bitmap, { copyResult ->
                                if (copyResult == PixelCopy.SUCCESS) {
                                    callback(bitmap)
                                }
                                // possible to handle other result codes ...
                            },
                            Handler(mainLooper)
                        )
                    } catch (e: IllegalArgumentException) {
                        // PixelCopy may throw IllegalArgumentException, make sure to handle it
                        e.printStackTrace()
                    }
                }
            }
    }

    // this function checks if the file is available or not and returns its size, return 0 if not exists
    private fun checkIfVideoAlreadyExists(name: String, inDir: File) : Long {
        if(name.contains(".mp4") || name.contains(".mkv")
            || name.contains(".mov") || name.contains(".webm")
            || name.contains(".flv")) {
            if (inDir.exists()) {
                val allFilesNames = inDir.list()
                if (allFilesNames != null && allFilesNames.contains(name)) {
                    return File(inDir, name).length()
                }
            }
        }
        return 0
    }

    private suspend fun getMyPublicIpAsync() : Deferred<String> =
        coroutineScope {
            async(Dispatchers.IO) {
                val result = try {
                    val url = URL("https://api.ipify.org")
                    val httpsURLConnection = url.openConnection()
                    val iStream = httpsURLConnection.getInputStream()
                    val buff = ByteArray(1024)
                    val read = iStream.read(buff)
                    String(buff,0, read)
                } catch (e: Exception) {
                    "error : $e"
                }
                return@async result
            }
        }
}