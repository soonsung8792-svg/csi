package com.lab.idcam

import android.app.Application
import android.content.Context

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        Store.load()
    }

    companion object {
        lateinit var ctx: Context
    }
}
