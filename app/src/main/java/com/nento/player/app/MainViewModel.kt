package com.nento.player.app

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    val isScreenPaired = MutableLiveData(false)
    val isOffline = MutableLiveData(false)
    val isIdHidden = MutableLiveData(false)
}