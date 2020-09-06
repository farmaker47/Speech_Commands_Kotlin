package com.george.speech_commands_kotlin.di

import com.george.speech_commands_kotlin.RecognizeCommands
import org.koin.dsl.module

val recognizeCommandsModule = module {

    factory { RecognizeCommands(get()) }

}