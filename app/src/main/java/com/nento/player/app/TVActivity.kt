package com.nento.player.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class TVActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tvactivity)
        askStoragePermission()
        val nextButton = findViewById<Button>(R.id.nextBtn)
        nextButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please provide overlay permission", Toast.LENGTH_SHORT).show()
                checkDrawOverlayPermission2()
                return@setOnClickListener
            }
            startMainActivity()
        }
    }

    private fun askStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkDrawOverlayPermission2()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 10)
            } else {
                startMainActivity()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("WrongConstant")
    private fun checkDrawOverlayPermission2() {
        try {
            val canDraw = Settings.canDrawOverlays(this)
            Log.d("CanDrawOverlay", "$canDraw")
            if (!canDraw) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                //      intent.flags = 268435456
                startActivity(intent)
            } else {
                startMainActivity()
            }
        } catch (e: Exception) {
            val builder = AlertDialog.Builder(this).apply {
                setMessage("Sorry! we can't open permission settings for you, please do it manually" +
                        " go to Setting -> Apps -> Special app access -> Display over other apps, and enable Player App")
                setPositiveButton("OK") { _,_ -> }
            }
            val aDialog = builder.create()
            aDialog.setTitle("Provide Overlay Permission")
            aDialog.show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startMainActivity()
    }

    private fun startMainActivity() {
        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.putExtra(Constants.INTENT_CRASH_RECOVERY,
            intent.getBooleanExtra(Constants.INTENT_CRASH_RECOVERY, false))
        startActivity(mainIntent)
        finish()
    }

}