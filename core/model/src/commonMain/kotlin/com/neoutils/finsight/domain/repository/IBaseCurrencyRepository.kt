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
}

/*
 * **There is no setter, and its absence is the design.**
 *
 * Switching bases is pure derivation — no converted value is persisted, so the rate of
 * the old base against the new one is the inverse of one already stored and the rest
 * re-express by triangulation over rates of the same date. What this change owes that
 * future is that nothing here makes it *impossible*, and nothing does.
 *
 * A setter that only wrote the new code would be the opposite of that guarantee. Every
 * rate on file is denominated against the base it was written under; rewriting the code
 * without re-expressing them leaves the archive being read against a base it was never
 * measured in — every consolidated figure in the history silently wrong, with no error,
 * no mark and no way for the user to tell. Offering the switch means shipping the
 * re-expression with it, and that is a change of its own.
 */
