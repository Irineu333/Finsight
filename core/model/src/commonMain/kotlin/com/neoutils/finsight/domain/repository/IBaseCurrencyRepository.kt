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
 * to it changing; v1 offers no screen that changes it (design D28), so what actually
 * moves at runtime is the rate archive. The flow exists so that offering the change
 * later does not mean rewriting every read.
 *
 * It is resolved once, from the device's region, on the first run — and a later trip
 * abroad MUST NOT move it, which would silently re-express the whole history.
 */
interface IBaseCurrencyRepository {

    /** The base currency in force, seeded on first run and stable afterwards. */
    fun observe(): StateFlow<String>

    /**
     * Sets the base currency.
     *
     * No screen calls this in v1. It exists because nothing about the change may make
     * it *impossible*: no converted value is persisted, so switching bases is pure
     * derivation — the rate of the old base against the new one is the inverse of one
     * already stored, and the rest re-express by triangulation over rates of the same
     * date. Offering it is a change of its own.
     */
    suspend fun set(currency: String)
}
