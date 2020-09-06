package com.george.speech_commands_kotlin

import android.app.Application
import com.george.speech_commands_kotlin.di.mainViewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SpeechCommandsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            //androidContext(applicationContext)
            androidContext(this@SpeechCommandsApplication)
            modules(mainViewModelModule)
        }
    }
}