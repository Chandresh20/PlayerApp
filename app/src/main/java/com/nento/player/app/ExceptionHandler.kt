package com.nento.player.app

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.os.Process
import android.util.Log
import io.sentry.Sentry
import java.lang.Exception
import kotlin.system.exitProcess

class ExceptionHandler(private val activity: Activity) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(p0: Thread, p1: Throwable) {
        Log.e("UncaughtException", "$p0: ${p1.message}")
        MainActivity.sendToSentry("Caught: ${p1.message}")
        val intent = Intent(activity.applicationContext, TVActivity::class.java)
        try {
            intent.putExtra(Constants.INTENT_CRASH_RECOVERY, true)
            val pIntent = PendingIntent.getActivity(activity.applicationContext,
                0, intent, PendingIntent.FLAG_ONE_SHOT)
            val arm = activity.applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
            arm.set(AlarmManager.RTC, System.currentTimeMillis() + 5000, pIntent)
            Process.killProcess(Process.myPid())
            exitProcess(2)
        } catch (e: Exception) {
            Log.e("UncaughtNew", "$e")
        }
    }
}