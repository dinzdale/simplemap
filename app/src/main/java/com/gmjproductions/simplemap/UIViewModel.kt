package com.gmjproductions.simplemap

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class UIViewModel : ViewModel() {
    private val _showProgressBar = MutableLiveData(false)
    val showProgressBar : LiveData<Boolean> = _showProgressBar

    fun showProgressBar(showProgressBar:Boolean) {
        _showProgressBar.value = showProgressBar
    }

}