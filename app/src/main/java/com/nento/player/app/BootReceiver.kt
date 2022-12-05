package com.nento.player.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlin.system.exitProcess

class BootReceiver : BroadcastReceiver() {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(p0: Context?, p1: Intent?) {
     //   doRestart(p0)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun doRestart(c: Context?) {
        try {
            //check if the context is given
            if (c != null) {
                //fetch the packageManager so we can get the default launch activity
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
                        val mgr = c.getSystemService(Application.ALARM_SERVICE) as AlarmManager
                        mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] =
                            mPendingIntent
                        //kill the application
                        exitProcess(0)
                    } else {
                        showRestart("Was not able to restart application, mStartActivity null",c)

                    }
                } else {
                    // Log.e(TAG, "Was not able to restart application, PM null")
                    showRestart("Was not able to restart application, PM null",c)

                }
            } else {
                // Log.e(TAG, "Was not able to restart application, Context null")
             //   showRestart("Was not able to restart application, Context null",c!!)

            }
        } catch (ex: java.lang.Exception) {
            // Log.e(TAG, "Was not able to restart application")
            showRestart("Was not able to restart application",c!!)
        }
    }

    private fun showRestart(msg:String, context:Context){
        Toast.makeText(context,msg,Toast.LENGTH_LONG).show()
    }
}