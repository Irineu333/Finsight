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
 * The locale, which is the source the app has always formatted money with — the same one
 * the legacy relabel of design D30 reads, and for a reason of the same shape: neither
 * asks where the user is, both ask what the user has been reading. Here the answer only
 * decides how a **consolidated** figure reads, appears nowhere at all for a
 * single-currency user, and the user can change it.
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
