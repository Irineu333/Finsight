package com.neoutils.finsight.ui.modal.invoicePayment

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.safeOnDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

/**
 * The days on which this invoice may be settled.
 *
 * The state decides it, as it decides everything else about a payment. An invoice still
 * taking spending is paid **inside its own cycle**; one that has closed is paid between
 * its closing and its due date. Nothing is ever paid after today.
 *
 * ```
 * OPEN, RETROACTIVE  [openingDate, min(closingDate, today)]
 * CLOSED             [closingDate, min(dueDate,     today)]
 * ```
 *
 * A retroactive cycle lies wholly in the past, so its whole window does too — a payment
 * dated months back is what regularizing a past cycle means, not a defect.
 *
 * This is a **limit**, not a suggestion: the use cases refuse what falls outside it
 * (`AdvanceInvoicePaymentUseCase` over the cycle, `PayInvoiceUseCase` over the closing
 * and due dates), so a form that offered more would only be offering a refusal.
 *
 * The upper edge is pulled up to the lower one when an invoice closed before its closing
 * day: the range stays inhabited, and the domain still has the last word on the date.
 */
internal fun Invoice.settlementWindow(today: LocalDate): ClosedRange<LocalDate> {
    val start = if (acceptsFullSettlement) closingDate else openingDate
    val end = (if (acceptsFullSettlement) dueDate else closingDate).coerceAtMost(today)
    return start..end.coerceAtLeast(start)
}

/**
 * The date inside this range that falls on [day].
 *
 * The day is what is preserved and the range decides the month — the rule
 * `InvoiceWindow.dateOn` states for a cycle, read here over a range that may span the
 * closing and the due month instead. A day the range admits in neither month is pulled to
 * the nearest edge, because here the range binds rather than suggests.
 */
internal fun ClosedRange<LocalDate>.dateOn(day: Int): LocalDate {
    val early = start.yearMonth.safeOnDay(day)
    val late = endInclusive.yearMonth.safeOnDay(day)

    return when {
        early in this -> early
        late in this -> late
        else -> early.coerceIn(this)
    }
}
