package com.neoutils.finsight.di

import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.util.DebounceManager
import com.russhwolf.settings.Settings
import org.koin.dsl.module

/**
 * Singletons de infraestrutura cross-cutting providos pelo core common
 * (persistência de settings, formatação de moeda, debounce), consumidos
 * por múltiplas features via Koin.
 */
val commonModule = module {
    single<Settings> { Settings() }
    single { CurrencyFormatter() }
    factory { DebounceManager(delayMillis = 500L) }
}
