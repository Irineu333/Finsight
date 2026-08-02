package com.neoutils.finsight.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * The user's **base currency**: the one consolidated figures are expressed in.
 *
 * It is a display preference and nothing more. The ledger has no opinion on it — every
 * figure there is `Σ entries`, per currency — and it appears on screen **only** where a
 * consolidation actually happened. A balance the ledger answered in one currency is
 * shown in *that* currency, whatever the base is (design D8, D9, D29).
 *
 * **Observable, and that is a requirement of shape.** Every consolidated figure reacts
 * to it changing, and the change is offered: settings switches it, and every figure on
 * screen re-expresses on the next read.
 *
 * **Switching writes the preference and nothing else.** No stored row moves, no
 * migration runs, nothing is re-expressed on write — every rate on file names both of
 * its ends, so none of them changes meaning when the preference does. The whole
 * re-expression is a read, and it has an owner of its own in the rate repository.
 *
 * It is resolved once, from the device's **locale**, on the first run — and a later trip
 * abroad MUST NOT move it, which would silently re-express the whole history.
 *
 * The locale, and deliberately not [com.neoutils.finsight.extension.DeviceRegion]. The
 * two are not interchangeable and the difference is the whole of design D30: on Android
 * the locale's country comes from the language list, so *English (United States)* is
 * `en-US` whatever the user's money is. That is fatal for the legacy relabel, which
 * rewrites stored rows and cannot be undone, and harmless here — the base only decides
 * how a **consolidated** figure reads, appears nowhere for a single-currency user, and is
 * seeded from the same source the app has always formatted money with.
 */
interface IBaseCurrencyRepository {

    /** The base currency in force, seeded on first run and moved only by [set]. */
    fun observe(): StateFlow<String>

    /**
     * Switches the base currency: persists [code] and emits it. That is the whole
     * operation — there is no use case, because there is no case, only a preference
     * being written (design D5).
     */
    suspend fun set(code: String)
}
