package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.LocalDate

/**
 * The **one** builder of a category breakdown: natural per-subject totals in, the list
 * a surface renders out.
 *
 * Every producer assembles its own totals — the perimeters genuinely differ, a month
 * of the dashboard is not a report's range seen from a perspective — and delegates
 * everything downstream of them, which is the part that must not differ: the sign, the
 * discarding of what nets to zero, the one comparative scale, the ordering, and the
 * share.
 *
 * Five rules, in the order they apply:
 *
 * 1. **The display sign is applied term by term.** It is a property of the nature, not
 *    of the currency, and it is what turns both an expense and an income into a
 *    positive figure.
 * 2. **A subject that nets to zero is dropped**, unclassified included — a period with
 *    nothing unclassified produces exactly the breakdown it produced before the line
 *    existed.
 * 3. **One scale over everything that survived**, [SpendingSubject.Uncategorized]
 *    included as a key of it. That is what makes the shares add up to the period: a
 *    scale built over the resolved categories alone would hand them 100% of a whole
 *    that is not the whole.
 * 4. **Descending magnitude, with the unclassified pinned last** whatever its size, and
 *    a subject with no magnitude last within its group — it cannot be ranked against
 *    the others, so it is not ordered by accident.
 * 5. **The share comes off that same scale**, and is `null` rather than `0%` when there
 *    is no answer.
 *
 * No label is decided here (nor an icon, nor a click): the domain carries no
 * user-facing text, and whoever renders the line resolves it from the subject.
 */
suspend fun ConsolidateMoneyUseCase.spendingBreakdown(
    totals: Map<SpendingSubject, MoneyByCurrency>,
    displaySign: Int,
    on: LocalDate,
): List<CategorySpending> {
    val amounts = totals.mapNotNull { (subject, natural) ->
        val amount = MoneyByCurrency.of(natural.toList().associate { it.currency to it.value * displaySign })
        if (amount.isEmpty || amount.toList().all { it.value == 0.0 }) null else subject to amount
    }

    val scale = comparativeMagnitudes(figures = amounts.toMap(), on = on)

    return amounts
        .sortedWith(
            // The unclassified line is not competing for a position: it is last by
            // rule, and only the categories are ranked among themselves.
            compareBy<Pair<SpendingSubject, MoneyByCurrency>> { (subject, _) ->
                subject is SpendingSubject.Uncategorized
            }.thenByDescending { (subject, _) ->
                scale.magnitudeOf(subject) ?: Double.NEGATIVE_INFINITY
            }
        )
        .map { (subject, amount) ->
            CategorySpending(
                subject = subject,
                // The display sign above already made this a positive figure; the line
                // reads its direction off its own section's title.
                amount = invoke(
                    money = amount,
                    on = on,
                    policy = DisplayAmount::magnitude,
                ),
                percentage = scale.shareOf(subject)?.let { it * 100 },
            )
        }
}
