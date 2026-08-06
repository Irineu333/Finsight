package com.neoutils.finsight

import com.neoutils.finsight.feature.support.api.ISupportRepository
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * What a debug build adds to the app's graph, aggregated after `appModules` so that a definition
 * declared here replaces the one shipped by a core module or a feature.
 *
 * Both definitions below replace something a device cannot be asked for. The clock:
 * [ShiftableClock] takes the place of the `Clock.System` bound in `:core:common`, so a test can
 * move time. It is bound twice on purpose — everything that reads the time asks for `Clock` and
 * cannot move it; only [applyTimeTravel], which resolves the concrete type, can. And support:
 * [InMemorySupportRepository] takes the place of the Firestore-backed one, so the support journey
 * can be driven without network, credentials, or rows left behind in a real project.
 *
 * Naming `ISupportRepository` here rather than the implementation is what keeps this legal: the
 * app sees every feature's `api` and none of their `impl`s.
 */
val debugModule = module {
    single { ShiftableClock() }
    single<Clock> { get<ShiftableClock>() }

    single<ISupportRepository> { InMemorySupportRepository(clock = get()) }
}
