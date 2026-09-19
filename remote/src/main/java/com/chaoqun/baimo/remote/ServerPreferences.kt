package com.chaoqun.baimo.remote

import android.content.Context

class ServerPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = ServerUrl.normalize(prefs.getString(KEY_URL, ServerUrl.DEFAULT).orEmpty())
        set(value) {
            prefs.edit().putString(KEY_URL, ServerUrl.normalize(value)).apply()
        }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) {
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
        }

    fun resetUrl() {
        serverUrl = ServerUrl.DEFAULT
    }

    private companion object {
        const val PREFS_NAME = "baimo_remote"
        const val KEY_URL = "server_url"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    }
}
