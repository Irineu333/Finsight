@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.di

import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.CurrencySymbols
import com.neoutils.finsight.extension.symbolOf
import com.neoutils.finsight.util.DebounceManager
import com.russhwolf.settings.Settings
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val commonModule = module {
    single<Settings> { Settings() }

    // The glyph comes from the table, read live: this is the instance the two form view
    // models format with, outside composition, where there is nothing to recompose and
    // so nothing that a snapshot would keep up to date. `FormattingLocalsHost` derives
    // its own from a collected snapshot, for the opposite reason.
    single { CurrencyFormatter(symbolOf = { code -> get<CurrencySymbols>().symbols.value.symbolOf(code) }) }

    factory { DebounceManager(delayMillis = 500L) }

    // The app reads the time from here rather than from `Clock.System` at each call site, so that
    // a build which needs to move it — only a debug one does, to reach behaviour that requires
    // time to have passed — can bind another clock over this one. This module ships the real one.
    single<Clock> { Clock.System }
}
