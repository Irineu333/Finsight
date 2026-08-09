package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.model.ExchangeRate
import kotlinx.datetime.LocalDate

/**
 * A rate the archive **implies** rather than holds, and the origin it may honestly claim.
 *
 * The origin is not decoration. While the field meant *"not the user's"* labelling every
 * implied answer `DERIVED` was legitimate; with three origins it is a lie, and the one
 * place it would be read — the precedence — is exactly where a lie costs something.
 *
 * It is the origin of the observation read when there is only one (the **inverse** is the
 * *same* observation read backwards, so it keeps that observation's origin), and the
 * **weakest** of the two in a triangulation — which is well defined precisely because the
 * precedence is a total order.
 *
 * **None of this is writable.** The implied answer has no `id`, and it goes on being
 * unable to return to the archive.
 */
internal data class ResolvedRate(
    val rate: Double,
    val source: ExchangeRate.Source,
)

/**
 * How much of `to` one unit of `from` is worth, out of a set of observations that have
 * **already been resolved by the archive's own policy** — one per pair, the latest on or
 * before the date, ties on that date broken by origin.
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
 *
 * **The answer carries an origin, and it is the honest one** — see [ResolvedRate]. A
 * currency against itself is `1` by definition and no observation produced it, so it
 * claims the weakest origin there is: the answer never asserts more authority than the
 * archive actually holds.
 */
internal fun List<ExchangeRate>.resolve(from: String, to: String): ResolvedRate? {
    if (from == to) return ResolvedRate(1.0, ExchangeRate.Source.DERIVED)

    direct(from, to)?.let { return ResolvedRate(it.rate, it.source) }
    direct(to, from)?.let { return ResolvedRate(1.0 / it.rate, it.source) }

    return bestPivot(from, to)
}

/** The total order the archive's precedence is: `USER` ▸ `REMOTE` ▸ `DERIVED`. */
private val ExchangeRate.Source.rank: Int
    get() = when (this) {
        ExchangeRate.Source.USER -> 0
        ExchangeRate.Source.REMOTE -> 1
        ExchangeRate.Source.DERIVED -> 2
    }

private fun weakest(a: ExchangeRate.Source, b: ExchangeRate.Source) = if (a.rank >= b.rank) a else b

private fun List<ExchangeRate>.direct(from: String, to: String): ExchangeRate? =
    firstOrNull { it.currency == from && it.counterCurrency == to }

/**
 * One leg, resolved by levels 1 and 2 only — a pivot's leg may not itself pivot — and
 * carrying the origin it was read from.
 */
private fun List<ExchangeRate>.leg(from: String, to: String): Triple<Double, LocalDate, ExchangeRate.Source>? {
    direct(from, to)?.let { return Triple(it.rate, it.date, it.source) }
    direct(to, from)?.let { return Triple(1.0 / it.rate, it.date, it.source) }
    return null
}

/** The deterministic pivot, whose answer declares the **weakest** of its two legs. */
private fun List<ExchangeRate>.bestPivot(from: String, to: String): ResolvedRate? {
    val candidates = (map { it.currency } + map { it.counterCurrency })
        .distinct()
        .filter { it != from && it != to }
        .sorted()

    var bestDates: Pair<LocalDate, LocalDate>? = null
    var best: ResolvedRate? = null

    for (pivot in candidates) {
        val first = leg(from, pivot) ?: continue
        val second = leg(pivot, to) ?: continue

        // Most recent legs win; the codes are already ascending, so the first candidate
        // to reach a given pair of dates keeps it — which is the tie-break, and it is
        // total.
        val older = minOf(first.second, second.second)
        val newer = maxOf(first.second, second.second)
        val current = bestDates
        if (current == null || newer > current.second || (newer == current.second && older > current.first)) {
            bestDates = older to newer
            best = ResolvedRate(
                rate = first.first * second.first,
                source = weakest(first.third, second.third),
            )
        }
    }

    return best
}
