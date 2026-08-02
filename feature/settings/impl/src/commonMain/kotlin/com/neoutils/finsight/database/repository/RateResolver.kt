package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.model.ExchangeRate
import kotlinx.datetime.LocalDate

/**
 * How much of `to` one unit of `from` is worth, out of a set of observations that have
 * **already been resolved by the archive's own policy** — one per pair, the latest on or
 * before the date, the user's over the derived one.
 *
 * That split is the design: the date-and-origin policy has one owner, in the DAO's
 * query; what lives here is the other question, which of several *paths* answers when
 * more than one reaches. Both are needed, and confusing them would give the same
 * question more than one answer depending on the order rows came back in — which kills
 * the property this code protects hardest: knowing where a number came from.
 *
 * The precedence, and it stops at the first level that answers:
 *
 * ```
 * 1. DIRECT    (from, to)
 * 2. INVERSE   (to, from) → 1/r
 * 3. ONE PIVOT (from, P) and (P, to), levels 1 and 2 again on each leg
 * ```
 *
 * **The inverse precedes the pivot** because it is the *same* observation read
 * backwards, while a pivot is two others: preferring the pivot would prefer more sources
 * of error to fewer.
 *
 * **One hop, never two.** Chaining triangulations compounds three roundings and three
 * independent observations into a number no screen can explain, and a two-hop path is
 * always an archive missing the obvious observation. There the right answer is *there is
 * no rate*, and the figure keeps a term of its own — the same line design D9 already
 * draws for an absent rate, applied to an absent path. `null` MUST NOT become `1`.
 *
 * **The pivot is chosen deterministically**: the one whose two legs have the most recent
 * dates, ties broken by the ISO code ascending. The second criterion is arbitrary on
 * purpose — what matters about it is being total, not being fair.
 */
internal fun List<ExchangeRate>.resolveRate(from: String, to: String): Double? {
    if (from == to) return 1.0

    direct(from, to)?.let { return it.rate }
    direct(to, from)?.let { return 1.0 / it.rate }

    return bestPivot(from, to)
}

private fun List<ExchangeRate>.direct(from: String, to: String): ExchangeRate? =
    firstOrNull { it.currency == from && it.counterCurrency == to }

/** One leg, resolved by levels 1 and 2 only — a pivot's leg may not itself pivot. */
private fun List<ExchangeRate>.leg(from: String, to: String): Pair<Double, LocalDate>? {
    direct(from, to)?.let { return it.rate to it.date }
    direct(to, from)?.let { return 1.0 / it.rate to it.date }
    return null
}

private fun List<ExchangeRate>.bestPivot(from: String, to: String): Double? {
    val candidates = (map { it.currency } + map { it.counterCurrency })
        .distinct()
        .filter { it != from && it != to }
        .sorted()

    var best: Triple<LocalDate, LocalDate, Double>? = null

    for (pivot in candidates) {
        val first = leg(from, pivot) ?: continue
        val second = leg(pivot, to) ?: continue

        // Most recent legs win; the codes are already ascending, so the first candidate
        // to reach a given pair of dates keeps it — which is the tie-break, and it is
        // total.
        val older = minOf(first.second, second.second)
        val newer = maxOf(first.second, second.second)
        val current = best
        if (current == null || newer > current.second || (newer == current.second && older > current.first)) {
            best = Triple(older, newer, first.first * second.first)
        }
    }

    return best?.third
}
