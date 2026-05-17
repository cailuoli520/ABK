package com.abk.kernel

import android.app.Application
import android.util.Log
import com.abk.kernel.utils.AbkManagerIdentity
import com.abk.kernel.utils.NotificationUtils
import com.abk.kernel.utils.RootUtils

class AbkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RootUtils.init(this)
        NotificationUtils.createChannels(this)
        AbkManagerIdentity.verifySelf(this).mismatchSummary()?.let { summary ->
            Log.w("ABK", "Manager identity mismatch: $summary")
        }
    }
}
