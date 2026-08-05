package com.neoutils.finsight.di

import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.CurrencySymbols
import com.neoutils.finsight.extension.symbolOf
import com.neoutils.finsight.util.DebounceManager
import com.russhwolf.settings.Settings
import org.koin.dsl.module

val commonModule = module {
    single<Settings> { Settings() }

    // The glyph comes from the table, read live: this is the instance the two form view
    // models format with, outside composition, where there is nothing to recompose and
    // so nothing that a snapshot would keep up to date. `FormattingLocalsHost` derives
    // its own from a collected snapshot, for the opposite reason.
    single { CurrencyFormatter(symbolOf = { code -> get<CurrencySymbols>().symbols.value.symbolOf(code) }) }

    factory { DebounceManager(delayMillis = 500L) }
}
