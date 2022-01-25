package com.example.masterproject

import android.app.Application
import android.content.Context
import android.util.Log

class App: Application() {

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
    }

    companion object {
        private var context: Context? = null

        fun getAppContext(): Context? {
            return context
        }
    }
}