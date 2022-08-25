package com.nento.player.app.fragment

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.nento.player.app.*
import com.nento.player.app.api.ApiService
import com.nento.player.app.databinding.FragmentMainScreenBinding
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.Runnable

class MainScreenFragment : Fragment() {

    private lateinit var binding: FragmentMainScreenBinding
    private lateinit var blinkHandler : Handler
    private lateinit var blinkRunnable : Runnable
    private lateinit var blinkRunnable2 : Runnable
    lateinit var ctx: Context
    private val blinkDuration = 500L

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ctx = findNavController().context
        Constants.onSplashScreen = true
        binding = FragmentMainScreenBinding.inflate(inflater, container, false)
        binding.screenID.text = Constants.screenID
        binding.updateBtn.setOnClickListener {
            ctx.sendBroadcast(Intent(Constants.CHECK_UPDATE_BROADCAST))
        }
        binding.resetBtn.setOnClickListener {
            Toast.makeText(ctx, "hold reset button to Reset App", Toast.LENGTH_LONG).show()
        }
        var countDown = 4
        binding.resetBtn.setOnTouchListener { _, motionEvent ->
            val touchHandler = Handler(Looper.getMainLooper())
            val touchRunnable = object : Runnable {
                override fun run() {
                    countDown -= 1
                    if (countDown == 0) {
                        Toast.makeText(ctx, "App reset", Toast.LENGTH_SHORT).show()
                        resetApp()
                    } else if (countDown > 0) {
                        touchHandler.postDelayed(this, 1000)
                    }
                }
            }
            when(motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    countDown = 4
                    touchHandler.post(touchRunnable)
                }
                MotionEvent.ACTION_UP -> {
                    countDown = -1
                    touchHandler.removeCallbacks(touchRunnable)
                }
            }
            false
        }
        binding.setWifiButton.setOnClickListener {
            MainActivity.pauseForWifi = true
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        val updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                try {
                    findNavController().navigate(R.id.action_mainScreenFragment_to_navigation_media)
              //      startActivity(Intent(ctx, MediaActivity::class.java))
                } catch (e: Exception) {
                    Log.e("MainScreen", "navControllerError $e")
                }
                ctx.unregisterReceiver(this)
            }
        }
        ctx.registerReceiver(updateReceiver, IntentFilter(Constants.START_MEDIA_BROADCAST))
        blinkHandler = Handler(Looper.getMainLooper())
        blinkRunnable = Runnable {
            binding.pairedText.animate().alpha(0f).duration = blinkDuration
            blinkHandler.postDelayed(blinkRunnable2, blinkDuration)
        }
        blinkRunnable2 = Runnable {
            binding.pairedText.animate().alpha(1f).duration = blinkDuration
            blinkHandler.postDelayed(blinkRunnable, blinkDuration)
        }
        return binding.root
    }

    private fun resetApp() {
        // send reset info to server
        MainActivity.resetApp(ctx.applicationContext)
        val resetJson = JSONObject()
        resetJson.put("screenNumber", Constants.screenID)
        resetJson.put("type", "reset_screen_app")
        ApiService.apiService.sendResetCommandToServer(resetJson.toString(),
            "application/json").enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                Log.d("ResetCommand", "${response.body()}")
            }
            override fun onFailure(call: Call<String>, t: Throwable) {
                Log.e("ResetCommand", "${t.message}")
            }
        })
        (ctx as MainActivity).finish()
    }


    override fun onResume() {
        super.onResume()
        MainActivity.mainViewModel.isScreenPaired.observe(viewLifecycleOwner) { show ->
            if (show) {
                if (MainActivity.mainViewModel.isOffline.value == false) {
                    binding.pairedText.visibility = View.VISIBLE
                    blinkHandler.post(blinkRunnable)
                }
            } else {
                blinkHandler.removeCallbacks(blinkRunnable)
                blinkHandler.removeCallbacks(blinkRunnable2)
                binding.pairedText.visibility = View.GONE
            }
        }
        MainActivity.mainViewModel.macAddress.observe(viewLifecycleOwner) { mac ->
            binding.macAddressText.text = mac
        }
        MainActivity.mainViewModel.isOffline.observe(viewLifecycleOwner) { offline ->
            if (!offline) {
                binding.macAddressText.visibility = View.GONE
                binding.setWifiButton.visibility = View.GONE
            } else {
                binding.macAddressText.visibility = View.VISIBLE
                binding.setWifiButton.visibility = View.VISIBLE
            }
        }
    }

    override fun onPause() {
        blinkHandler.removeCallbacks(blinkRunnable)
        blinkHandler.removeCallbacks(blinkRunnable2)
        super.onPause()
    }

    override fun onDestroy() {
        blinkHandler.removeCallbacks(blinkRunnable)
        blinkHandler.removeCallbacks(blinkRunnable2)
        super.onDestroy()
    }
}