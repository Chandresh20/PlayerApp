package com.nento.player.app

import org.json.JSONArray
import kotlin.math.roundToInt

class Constants {

    companion object {

        const val CONNECTION_TIMEOUT = 20L
        const val READ_TIMEOUT = 20L
        const val WRITE_TIMEOUT = 20L
        const val BASE_URL = "https://react.tjcg.in/"
    //    const val BASE_URL = "https://signage.mycircle.net/"
        const val TEMPLATE_NAME ="template"
        const val PLAYLIST_FILE_NAME = "playlistObject"
        const val PLAYLIST_DIR_NAME = "Playlists"
        const val CUSTOM_LAYOUT_JSON_NAME = "Custom.json"
        const val CUSTOM_CONTENT_DIR = "Custom"
        const val DOWNLOAD_CONTENT_DIR = "Downloads"
        const val TYPE_TEMPLATE = "template"
        const val TYPE_PLAYLIST = "Playlist"
        const val TYPE_CUSTOM = "layout"
        const val C_PLAYLIST = "PLAYLIST"
        const val MEDIA_IMAGE = "IMAGE"
        const val MEDIA_VIDEO = "VIDEO"
        const val MEDIA_WEB_PAGE = "WEBPAGE"
        const val MEDIA_YOUTUBE = "YOUTUBE"
        const val STOP_VIDEO = "stop"

        const val CONTENT_ASSIGNED_TEMPLATE = 1
        const val CONTENT_ASSIGNED_PLAYLIST = 2
        const val CONTENT_ASSIGNED_CUSTOM = 3  // to show webView

        const val INTENT_CRASH_RECOVERY = "CrashRecovered"

        const val CUSTOM_WIDTH_THEIR = 500
        const val CUSTOM_HEIGHT_THEIR = 350

        const val PREFS_MAIN = "sharedPreferencesMain"
        const val PREFS_SCREEN_ID = "screenID"
        const val PREFS_CONTENT_ASSIGNED = "ContentAssigned"
        const val PREFS_CURRENT_TEMPLATE_VERTICAL = "isTemplateVertical"
        const val PREFS_CONTENT_ID = "ContentId"
        const val PREFS_SCREEN_PAIRED = "isScreenPaired"
        const val PREFS_PLAYER_ID = "playerID"
        const val PREFS_IS_RESET = "isReset"
        const val PREFS_IS_ID_HIDDEN = "isScreenIdHidden"
        const val PREFS_TEMPLATE_NAME = "templateName"
        const val PREFS_ROTATION_ANGLE = "rotationAngle"

        const val NEW_CONTENT_READY_BROADCAST = "newContentAvailable"
        const val NEW_TEMPLATE_READY_BROADCAST = "newTemplateAvailable"
        const val NEW_PLAYLIST_READY_BROADCAST = "newPlaylistReady"
        const val NEW_WEB_VIEW_READY_BROADCAST = "newWebViewReady"
          const val NEW_CUSTOM_LAYOUT_READY_BROADCAST = "newCustomLayoutReady"
        const val APP_DESTROYED_BROADCAST = "com.nento.player.destroyed111"
        const val START_MEDIA_BROADCAST = "startMediaNow"
        const val CHECK_UPDATE_BROADCAST = "checkForUpdate"

        //Socket Keys
        const val SOCKET_SCREEN_DATA = "screen_data"
        const val SOCKET_TEMPLATE_DATA = "use_our_template_data"
        const val SOCKET_PLAYLIST_DATA = "playlist_data"
        const val SOCKET_CUSTOM_DATA = "custom_layout_data"
        const val SOCKET_CHECK_ONLINE_STATUS = "CHECK_ONLINE_STATUS"
        const val SOCKET_CLOSE_SCREEN_APP = "CLOSE_SCREEN_APP"
        const val SOCKET_UPGRADE_SCREEN_APP = "UPGRADE_SCREEN_APP"
        const val SOCKET_RESET_SCREEN_APP = "RESET_SCREEN_APP"
        const val SOCKET_TAKE_SCREEN_SNAP_SHOT = "TAKE_SCREEN_SNAP_SHOT"
        const val SOCKET_CHECK_LAST_UPLOAD_DATA = "CHECK_LAST_UPLOAD_DATA"
        const val SOCKET_UPDATE_MEDIA_IN_SCREEN = "update_media_in_screen"
        const val SOCKET_ROTATE_SCREEN = "ROTATE_SCREEN"
        const val SOCKET_SHOW_HIDE_SCREEN_NUMBER = "SHOW_HIDE_SCREEN_NUMBER"

        //added after wifi-service
        const val PLAYER_APP_CLOSE_BROADCAST = "com.player.action.close"

        var screenID = ""
        var playerId = ""
        var deviceMemory = "0B"

        var onSplashScreen = true
        var rotationAngel = 0f
        var showWeather = false
        var showTime = false
        var weatherDataArray = JSONArray()
        var dateTimeDataArray = JSONArray()
    //    const val weatherAndTimeFontSizeMultiplier = 1.1f
    //    const val weatherIconMultiplier = 2f
        const val weatherAndTimeMargin = 25
        const val scaleMultiToMargin = 5
   //     const val weatherAndTimeFontSizeMultiplier720 = 1f
        const val weatherAndTimeTextLineSpacing1080 = 10f  // will be same for all screens
        const val weatherIconScalingConstant1080 = 33.3f
        const val weatherIconScalingConstant720 = 22.2f
        const val weatherIconScalingConstant4k = 66.6f

        const val APP_VERSION_CODE = 39
        const val APP_VERSION_NAME = "Beta36.2"
        const val APP_PLAYSTORE = ""

        fun getDayNameFromCal(num: Int) : String {
            return when(num) {
                1 -> "Sunday"
                2 -> "Monday"
                3 -> "Tuesday"
                4 -> "Wednesday"
                5 -> "Thursday"
                6 -> "Friday"
                7 -> "Saturday"
                else -> "error"
            }
        }

        fun getMonthNameFromCal(num : Int) : String {
            return when(num) {
                0 -> "January"
                1 -> "February"
                2 -> "March"
                3 -> "April"
                4 -> "May"
                5 -> "June"
                6 -> "July"
                7 -> "August"
                8 -> "September"
                9 -> "October"
                10 -> "November"
                11 -> "December"
                else -> "Error"
            }
        }

        fun convertCelcToFern(cel: Float) : Int {
            val fern = (cel * 9 /5) + 32
            return fern.roundToInt()
        }
    }
}