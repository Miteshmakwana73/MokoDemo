package com.example.mokodemo.application

import android.app.Application
import android.content.ContextWrapper
import com.moko.support.MokoSupport
import com.pixplicity.easyprefs.library.Prefs

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        MokoSupport.getInstance().init(applicationContext)

        Prefs.Builder().setContext(this).setMode(ContextWrapper.MODE_PRIVATE)
                .setPrefsName(getPackageName())
                .setUseDefaultSharedPreference(true)
                .build()
    }
}
