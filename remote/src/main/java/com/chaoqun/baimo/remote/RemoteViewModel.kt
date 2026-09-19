package com.chaoqun.baimo.remote

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = ServerPreferences(application)

    var serverUrl by mutableStateOf(prefs.serverUrl)
        private set

    var keepScreenOn by mutableStateOf(prefs.keepScreenOn)
        private set

    var progress by mutableIntStateOf(0)
        private set

    var pageError by mutableStateOf<String?>(null)
        private set

    var snackbar by mutableStateOf<String?>(null)
        private set

    var showSettings by mutableStateOf(false)

    var hasResultVideo by mutableStateOf(false)
        private set

    var savingToAlbum by mutableStateOf(false)
        private set

    fun updateProgress(value: Int) {
        progress = value
    }

    fun updatePageError(message: String?) {
        pageError = message
    }

    fun rememberResultAvailable(available: Boolean) {
        hasResultVideo = available
    }

    fun updateSavingToAlbum(saving: Boolean) {
        savingToAlbum = saving
    }

    fun showMessage(message: String) {
        snackbar = message
    }

    fun consumeSnackbar() {
        snackbar = null
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        prefs.keepScreenOn = enabled
        keepScreenOn = enabled
    }

    fun saveUrl(raw: String) {
        prefs.serverUrl = raw
        serverUrl = prefs.serverUrl
        pageError = null
        showSettings = false
        hasResultVideo = false
    }

    fun resetUrl() {
        prefs.resetUrl()
        serverUrl = prefs.serverUrl
        pageError = null
        hasResultVideo = false
    }
}
