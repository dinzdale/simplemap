package com.gmjproductions.simplemap

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class UIViewModel : ViewModel() {
    private val _showProgressBar = mutableStateOf(false)
    val showProgressBar  = _showProgressBar

    private val _snackbarMessage = MutableLiveData("")
    val snackbarMessage : LiveData<String> = _snackbarMessage

    fun showProgressBar(showProgressBar:Boolean) {
        _showProgressBar.value = showProgressBar
    }

    fun showSnackBarMessage(message:String="") {
        _snackbarMessage.value = message
    }
}