package com.neoutils.finsight

import org.koin.dsl.module
import kotlin.time.Clock

/**
 * What a debug build adds to the app's graph, aggregated after `appModules` so that a definition
 * declared here replaces the one shipped by a core module.
 *
 * Today that is the clock, and only the clock: [ShiftableClock] takes the place of the
 * `Clock.System` bound in `:core:common`, so a test can move time. It is bound twice on purpose —
 * everything that reads the time asks for `Clock` and cannot move it; only [applyTimeTravel],
 * which resolves the concrete type, can.
 */
val debugModule = module {
    single { ShiftableClock() }
    single<Clock> { get<ShiftableClock>() }
}
