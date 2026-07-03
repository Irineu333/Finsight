package com.neoutils.finsight.di

import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.util.DebounceManager
import com.russhwolf.settings.Settings
import org.koin.dsl.module

val commonModule = module {
    single<Settings> { Settings() }
    single { CurrencyFormatter() }
    factory { DebounceManager(delayMillis = 500L) }
}
