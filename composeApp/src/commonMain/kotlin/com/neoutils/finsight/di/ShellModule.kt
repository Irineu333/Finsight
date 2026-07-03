package com.neoutils.finsight.di

import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.DebounceManager
import com.russhwolf.settings.Settings
import org.koin.dsl.module

/**
 * Singletons de infraestrutura e de UI cross-cutting providos pelo shell
 * (persistência de settings, formatação de moeda, gerenciador de modais,
 * debounce) e consumidos por múltiplas features via Koin.
 */
val shellModule = module {
    single<Settings> { Settings() }
    single { CurrencyFormatter() }
    single { ModalManager() }
    factory { DebounceManager(delayMillis = 500L) }
}
