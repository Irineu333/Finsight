package com.neoutils.finance.di

import com.neoutils.finance.ui.component.ModalManager
import org.koin.dsl.module

val uiModule = module {
    single { ModalManager() }
}
