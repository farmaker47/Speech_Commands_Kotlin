package com.george.speech_commands_kotlin.di

import com.george.speech_commands_kotlin.MainActivityViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val mainViewModelModule = module {
    viewModel {
        MainActivityViewModel(get())
    }
}