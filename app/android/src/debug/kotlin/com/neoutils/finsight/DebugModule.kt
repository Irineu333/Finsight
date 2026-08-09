package com.neoutils.finsight

import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.feature.support.api.ISupportRepository
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * What a debug build adds to the app's graph, aggregated after `appModules` so that a definition
 * declared here replaces the one shipped by a core module or a feature.
 *
 * Every definition below replaces something a device cannot be asked for. The clock:
 * [ShiftableClock] takes the place of the `Clock.System` bound in `:core:common`, so a test can
 * move time. It is bound twice on purpose — everything that reads the time asks for `Clock` and
 * cannot move it; only [applyTimeTravel], which resolves the concrete type, can. And support:
 * [InMemorySupportRepository] takes the place of the Firestore-backed one, so the support journey
 * can be driven without network, credentials, or rows left behind in a real project. And the rate
 * source: [OfflineRateSource] takes the place of the Ktor-backed one, so the archive holds exactly
 * the rows a test wrote — a figure that needed a rate is otherwise a function of what a public API
 * published that morning, and *holding no rate at all* is not even reachable while a real source
 * keeps filling the archive behind the test.
 *
 * Naming `ISupportRepository` and `IRemoteRateSource` here rather than the implementations is what
 * keeps this legal: the app sees every feature's `api` and none of their `impl`s, and the rate port
 * is declared in `:core:model` precisely so that it can be named without naming a provider.
 */
val debugModule = module {
    single { ShiftableClock() }
    single<Clock> { get<ShiftableClock>() }

    single<ISupportRepository> { InMemorySupportRepository(clock = get()) }

    single<IRemoteRateSource> { OfflineRateSource() }
}
