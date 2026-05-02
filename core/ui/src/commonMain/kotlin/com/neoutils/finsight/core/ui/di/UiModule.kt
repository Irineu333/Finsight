package com.neoutils.finsight.core.ui.di

import com.neoutils.finsight.core.ui.extension.CurrencyFormatter
import com.neoutils.finsight.core.ui.component.ModalManager
import org.koin.dsl.module

val uiModule = module {
    single { ModalManager() }
    single { CurrencyFormatter() }
}
