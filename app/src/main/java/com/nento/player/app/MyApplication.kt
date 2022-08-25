package com.nento.player.app

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner

class MyApplication : Application(), LifecycleObserver {

    private  val TAG = "MyApplication"
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        //App in background
  //      doRestart(this)
    }
    fun doRestart(c: Context?) {
        try {
            //check if the context is given
            if (c != null) {
                //fetch the packagemanager so we can get the default launch activity
                // (you can replace this intent with any other activity if you want
                val pm = c.packageManager
                //check if we got the PackageManager
                if (pm != null) {
                    //create the intent with the default start activity for your application
                    val mStartActivity = pm.getLaunchIntentForPackage(
                        c.packageName
                    )
                    if (mStartActivity != null) {
                        mStartActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        //create a pending intent so the application is restarted after System.exit(0) was called.
                        // We use an AlarmManager to call this intent in 100ms
                        val mPendingIntentId = 223344
                        val mPendingIntent = PendingIntent
                            .getActivity(
                                c, mPendingIntentId, mStartActivity,
                                PendingIntent.FLAG_CANCEL_CURRENT
                            )
                        val mgr = c.getSystemService(ALARM_SERVICE) as AlarmManager
                        mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] =
                            mPendingIntent
                        //kill the application
                        System.exit(0)
                    } else {
                        Log.e(TAG, "Was not able to restart application, mStartActivity null")
                    }
                } else {
                    Log.e(TAG, "Was not able to restart application, PM null")
                }
            } else {
                Log.e(TAG, "Was not able to restart application, Context null")
            }
        } catch (ex: java.lang.Exception) {
            Log.e(TAG, "Was not able to restart application")
        }
    }
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        // App in foreground
    }
}