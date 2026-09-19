package com.chaoqun.depthwhite

import android.app.Application
import com.chaoqun.depthwhite.ml.ModelStore
import com.chaoqun.depthwhite.work.NotificationHelper

class DepthWhiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        ModelStore.warmupBundledSmall(this)
    }
}
