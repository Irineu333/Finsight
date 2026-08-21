package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.effectiveDay
import com.neoutils.finsight.extension.safeOnDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth

/**
 * The span a purchase falls in to belong to one invoice: `[openingDate, closingDate)`, both
 * on the card's closing day.
 *
 * An invoice has no single month. It opens on one, closes on the next and falls due on a
 * third, so the month a screen shows — the due month — names none of the days a purchase
 * could carry. This is the only description of an invoice that answers a date question.
 */
data class InvoiceWindow(
    val openingMonth: YearMonth,
    val closingMonth: YearMonth,
    val closingDay: Int,
) {
    /** The first day the window admits. */
    val openingDate get() = openingMonth.safeOnDay(closingDay)

    /** The first day the window no longer admits — it belongs to the next invoice. */
    val closingDate get() = closingMonth.safeOnDay(closingDay)

    /**
     * The last day the window admits — the day before [closingDate].
     *
     * The same span said the other way round: `[openingDate, closingDate)` and
     * `[openingDate, lastAdmittedDate]` cover the same days. It exists so that a caller owing an
     * **inclusive** end — a period, a report, a prose range — takes it from here instead of
     * subtracting a day of its own, or handing on [closingDate] and naming a day this window
     * refuses.
     */
    val lastAdmittedDate get() = closingDate.minus(1, DateTimeUnit.DAY)

    operator fun contains(date: LocalDate) = date >= openingDate && date < closingDate

    /**
     * The date inside this window that falls on [day].
     *
     * The day is what is preserved; the month is what the window decides. A day past the
     * closing day belongs to the segment before the turn, an earlier one to the segment
     * after it — the same rule `AddCreditCardUseCase` reads in the opposite direction to
     * place today's purchase.
     *
     * Idempotent: a date already inside the window is returned as it is, which is what lets
     * a form reproject on every invoice change without drifting.
     *
     * Total: a closing day at the end of a month can leave [day] in neither candidate month,
     * and the result is then pulled back to [openingDate] rather than escaping the window.
     */
    fun dateOn(day: Int): LocalDate {
        val late = closingMonth.safeOnDay(day)
        return (if (late < closingDate) late else openingMonth.safeOnDay(day))
            .coerceAtLeast(openingDate)
    }
}

/**
 * Whether the bill for a cycle arrives only in the month after it closes — which is what a
 * due day earlier in the month than the closing day means.
 *
 * The single statement of the rule. Everything relating a cycle to a due month reads it,
 * in one direction or the other, instead of restating the comparison.
 */
private val CreditCard.duePostponed get() = dueDay < closingDay

/**
 * The window an invoice due on [dueMonth] admits purchases in — whether or not that invoice
 * exists yet.
 */
fun CreditCard.invoiceWindowFor(dueMonth: YearMonth): InvoiceWindow {
    val closingMonth = if (duePostponed) dueMonth.minusMonth() else dueMonth
    return InvoiceWindow(
        openingMonth = closingMonth.minusMonth(),
        closingMonth = closingMonth,
        closingDay = closingDay,
    )
}

/**
 * The month an invoice closing on [closingMonth] falls due — the same rule read the other
 * way round, for the callers that build an invoice from its cycle rather than from its bill.
 */
fun CreditCard.dueMonthFor(closingMonth: YearMonth): YearMonth =
    if (duePostponed) closingMonth.plusMonth() else closingMonth

/**
 * The window that admits a purchase made on [date] — the cycle the card is in on that day.
 *
 * The inverse of [InvoiceWindow.dateOn]: that one asks which date a window implies, this one
 * which window a date falls in. The opening edge is inclusive in both, so a purchase made on
 * the closing day starts the next cycle rather than ending the one that closes.
 */
fun CreditCard.invoiceWindowOn(date: LocalDate): InvoiceWindow {
    val closingMonth = if (date.day < date.yearMonth.effectiveDay(closingDay)) {
        date.yearMonth
    } else {
        date.yearMonth.plusMonth()
    }

    return InvoiceWindow(
        openingMonth = closingMonth.minusMonth(),
        closingMonth = closingMonth,
        closingDay = closingDay,
    )
}

/**
 * The window of an invoice that exists, from the months it recorded.
 *
 * An existing invoice answers for what it stored, not for what its card's days would derive
 * today: the two cannot disagree because only one of them applies per case.
 */
val Invoice.window: InvoiceWindow
    get() = InvoiceWindow(
        openingMonth = openingMonth,
        closingMonth = closingMonth,
        closingDay = creditCard.closingDay,
    )
