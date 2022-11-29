package com.nento.player.app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class InitActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_init_acitivity)
        performInAppUpdate()
        startActivity(Intent(this, TVActivity::class.java))
    }

    private fun performInAppUpdate() {
        if (Constants.APP_PLAYSTORE.isNotBlank()) {
            Log.d("AppUpdate", "Looking for update")
            val appUpdateManager = AppUpdateManagerFactory.create(this)
            val updateInfoTask = appUpdateManager.appUpdateInfo
            updateInfoTask.addOnSuccessListener { appUpdateInfo ->
                if(appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    Log.d("AppUpdate", "`Available")
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        this,
                        101)
                } else {
                    Log.d("AppUpdate", "No Updates")
                }
            }
        }
    }
}