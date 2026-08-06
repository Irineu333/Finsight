@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.di

import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.util.DebounceManager
import com.neoutils.finsight.util.ShiftableClock
import com.russhwolf.settings.Settings
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val commonModule = module {
    single<Settings> { Settings() }
    single { CurrencyFormatter() }
    factory { DebounceManager(delayMillis = 500L) }

    // One clock, bound twice: everything that reads the time asks for `Clock` and cannot move it;
    // only whoever resolves the concrete type can. That is the debug-only reader of the launch
    // argument, and nothing else in the app.
    single { ShiftableClock() }
    single<Clock> { get<ShiftableClock>() }
}
