package com.george.speech.di

import com.george.speech.MainActivityViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val mainViewModelModule = module {
    viewModel {
        MainActivityViewModel(get())
    }
}