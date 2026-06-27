package com.moshbari.yaptext

import android.app.Application
import com.moshbari.yaptext.data.AppStorage
import com.moshbari.yaptext.data.SubscriptionManager

class YapTextApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStorage.init(this)
        SubscriptionManager.refresh()
    }
}
