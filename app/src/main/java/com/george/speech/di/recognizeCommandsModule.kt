package com.george.speech.di

import com.george.speech.RecognizeCommands
import org.koin.dsl.module

val recognizeCommandsModule = module {

    factory { RecognizeCommands(get()) }

}