package com.nento.player.app

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class InitActivity : AppCompatActivity() {

    // Codes by Magesh
    var mBluetoothAdapter: BluetoothAdapter? = null
    private var REQUIRED_PERMISSIONS: Array<String>? = null
    var rpl: ActivityResultLauncher<Array<String>>? = null
    var bluetoothActivityResultLauncher: ActivityResultLauncher<Intent>? = null
    // End here

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_init_acitivity)
        performInAppUpdate()
        startActivity(Intent(this, TVActivity::class.java))

        // Codes by Magesh
        if (ContextCompat.checkSelfPermission(this@InitActivity,
                android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@InitActivity,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this@InitActivity,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
            } else {
                ActivityCompat.requestPermissions(this@InitActivity,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }

        //setup the correct permissions needed, depending on which version. (31 changed the permissions.).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            REQUIRED_PERMISSIONS =
                arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
            //logthis("Android 12+, we need scan and connect.")
        } else {
            REQUIRED_PERMISSIONS =
                arrayOf(android.Manifest.permission.BLUETOOTH, android.Manifest.permission.BLUETOOTH_ADMIN)
            // logthis("Android 11 or less, bluetooth permissions only ")
        }

        //setup the correct permissions needed, depending on which version. (31 changed the permissions.).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            Toast.makeText(this, "Android 12+, we need scan and connect.", Toast.LENGTH_SHORT).show()
            val PERMISSIONS: Array<String> = arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            )
            ActivityCompat.requestPermissions(this@InitActivity, PERMISSIONS, 12);
        } else {
            Toast.makeText(this, "Android 11 or less, bluetooth permissions only", Toast.LENGTH_SHORT).show()
            val PERMISSIONS: Array<String> = arrayOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN,
            )
            ActivityCompat.requestPermissions(this@InitActivity, PERMISSIONS, 13);
        }

        bluetoothActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                // There are no request codes
                val data = result.data
                Toast.makeText(this, "Bluetooth is on.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please turn the bluetooth on", Toast.LENGTH_SHORT).show()
            }
        }
        // Magesh Codes ends here
    }

    //codes added by Magesh
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                    if ((ContextCompat.checkSelfPermission(
                            this@InitActivity,
                            android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED)) {
                        Toast.makeText(this, "Location Permission Granted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            Toast.makeText(this, "All Bluetooth permissions have been granted already.", Toast.LENGTH_SHORT).show()
            startbt()
        } else {
            Toast.makeText(this, "Not all Bluetooth permissions have been granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS!!) {
            if (ContextCompat.checkSelfPermission   (
                    this@InitActivity,
                    permission
                ) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun startbt() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(this, "This device does not support bluetooth", Toast.LENGTH_SHORT).show()
            return
        }
        //make sure bluetooth is enabled.
        if (!mBluetoothAdapter!!.isEnabled()) {
            Toast.makeText(this, "There is bluetooth, but turned off", Toast.LENGTH_SHORT).show()
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothActivityResultLauncher?.launch(enableBtIntent)
        } else {
            Toast.makeText(this, "The bluetooth is ready to use", Toast.LENGTH_SHORT).show()
        }
    }
    // Magesh codes ends here

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