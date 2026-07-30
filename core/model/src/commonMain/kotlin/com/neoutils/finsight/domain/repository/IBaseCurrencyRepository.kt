package com.neoutils.finsight.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * The user's **base currency**: the one a figure is reduced to when the ledger answered in
 * more than one, and nowhere else.
 *
 * It is a display preference, not an accounting fact. No account, entry, transaction or row
 * of the ledger carries it, nothing converted is ever persisted, and a user whose accounts
 * are all in one currency never sees it — not even when it differs from theirs, because
 * there was nothing to reconcile.
 *
 * **Observable**, in the mould of the dashboard's preferences: every consolidated figure
 * reacts to it changing, on the next read, retroactively and in full. That is what makes
 * changing it derivation rather than migration — and the reason this is a `StateFlow` and
 * not a getter is that a figure already on screen has to follow.
 *
 * It is resolved **once**, from the device's locale, and persisted. Changing the locale
 * afterwards must not move it: that would silently restate every consolidated figure in the
 * user's history because of a trip.
 */
interface IBaseCurrencyRepository {

    fun observe(): StateFlow<String>

    /**
     * The base now, for a caller that reads once rather than follows — a use case deciding
     * what currency to pre-select in a form. A caller that *renders* a figure observes.
     */
    fun current(): String = observe().value

    /**
     * Changing the base converts nothing and migrates nothing: the figures are recomputed on
     * the next read. The v1 offers no UI for it, and the operation exists so that the
     * implementation does not make offering it later impossible.
     */
    suspend fun set(currency: String)
}
