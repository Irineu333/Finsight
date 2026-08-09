package com.neoutils.finsight.domain.model

/**
 * The base currency in force, as something `core/database` can ask for.
 *
 * The rate archive's backfill needs it: every row written before the pair became explicit
 * was measured against the base in force, which until then had no way to change, so
 * naming it is exact rather than approximate.
 *
 * `core/database` cannot reach it on its own. The preference lives in `Settings` and its
 * repository lives in `feature/settings/impl`, which no core module may name. So it is
 * resolved where the preference is visible and arrives here as a plain code — the same
 * move [LegacyRelabel] already makes, for the same reason: the module below receives what
 * it may not name.
 */
fun interface SeededBaseCurrency {

    fun code(): String
}
