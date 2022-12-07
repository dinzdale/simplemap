package com.gmjproductions.simplemap

import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.gmjacobs.productions.openchargemap.model.geocode.GeocodeResult
import kotlinx.coroutines.flow.*

class UIViewModel : ViewModel() {
    private val _showProgressBar = mutableStateOf(false)
    val showProgressBar = _showProgressBar
    private val _locationEntry = MutableStateFlow<String>("")
    val locationEntry = _locationEntry
    val userEntry = locationEntry.debounce(1 * 1000)
    val searchEntry = mutableStateOf("")

    private val _snackbarMessage = MutableLiveData("")
    val snackbarMessage: LiveData<String> = _snackbarMessage

    fun showProgressBar(showProgressBar: Boolean) {
        _showProgressBar.value = showProgressBar
    }

    fun showSnackBarMessage(message: String = "") {
        _snackbarMessage.value = message
    }

    fun updateLocationEntry(entry: String) {
        _locationEntry.value = entry
    }

}