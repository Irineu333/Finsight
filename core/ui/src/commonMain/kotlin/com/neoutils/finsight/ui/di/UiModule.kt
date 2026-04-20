package com.neoutils.finsight.ui.di

import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.ui.component.ModalManager
import org.koin.dsl.module

val uiModule = module {
    single { ModalManager() }
    single { CurrencyFormatter() }
}
