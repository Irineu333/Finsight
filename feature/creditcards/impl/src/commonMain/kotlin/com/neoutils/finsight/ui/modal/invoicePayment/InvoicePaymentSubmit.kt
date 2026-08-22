package com.neoutils.finsight.ui.modal.invoicePayment

import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * Whether the payment may be submitted — one rule for both modes, because there is one
 * operation.
 *
 * **The ceiling holds over the card's side only**: [amount] and [outstandingDebt] are
 * both the card's money, while what leaves the account carries no ceiling at all — one
 * there would be a limit expressed in the wrong currency. That second field is still
 * *required* when the two ends differ (design D26), which is what keeps the write
 * boundary's same-sign guard unreachable by any path a user can walk.
 *
 * Where the invoice is discharged by the payment, the amount is not merely capped by what
 * is owed: it **is** what is owed. The field states it rather than accepting it, and this
 * refuses anything else so that the rule holds even if some path made the field editable.
 *
 * An invoice that owes nothing is not payable in either mode; discharging one that owes
 * nothing belongs to closing it.
 *
 * Top-level and `internal` so the rule can be exercised without a screen.
 */
internal fun canSubmitInvoicePayment(
    amount: String,
    paidAmount: String,
    isCrossCurrency: Boolean,
    settles: Boolean,
    date: String,
    window: ClosedRange<LocalDate>?,
    outstandingDebt: Double,
): Boolean {
    if (window == null) return false
    if (outstandingDebt <= 0.0) return false

    if (amount.isEmpty()) return false
    val parsedAmount = amount.moneyToDouble()
    if (parsedAmount <= 0.0) return false

    // Cents, because that is the resolution the field edits in and the ledger stores.
    if (settles && abs(parsedAmount - outstandingDebt) >= 0.005) return false
    if (!settles && parsedAmount > outstandingDebt) return false

    if (isCrossCurrency && paidAmount.moneyToDouble() <= 0.0) return false

    if (date.isEmpty()) return false
    val parsedDate = runCatching { dayMonthYear.parse(date) }.getOrElse { return false }

    return parsedDate in window
}
