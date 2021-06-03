package com.george.speech

import android.app.Application
import com.george.speech.di.mainViewModelModule
import com.george.speech.di.recognizeCommandsModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SpeechCommandsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            //androidContext(applicationContext)
            androidContext(this@SpeechCommandsApplication)
            modules(mainViewModelModule, recognizeCommandsModule)
        }
    }
}