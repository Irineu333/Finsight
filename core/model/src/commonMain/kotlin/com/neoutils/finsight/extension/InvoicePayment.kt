package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.LocalDate

/**
 * What stops this invoice from being paid on [date] — `null` when nothing does.
 *
 * **It exists so the answer is reached before the money moves.** Paying a bill is two writes: the
 * posting that takes the money out, and the invoice that is marked paid. They cannot be one atomic
 * write here, so whatever refuses the payment has to refuse it *before* the first — a refusal
 * discovered between them leaves the account short by a payment the app reports as not having
 * happened, and no compensating write puts it back.
 *
 * It is a derivation and not a second copy of the rule: the operation that marks the invoice paid
 * reads exactly this, so the answer given before the posting and the answer given after it are the
 * same answer, and can never drift into disagreeing.
 *
 * The order is the order the user would say it in — what the invoice *is*, then when it is being
 * paid — so the first thing they are told is the one that explains the rest.
 */
fun Invoice.paymentObstacleOn(date: LocalDate, today: LocalDate): InvoiceError? = when {
    // `isPayable` is the owner of which statuses accept a payment, and it accepts a retroactive
    // invoice as well as a closed one. Restating it as `status == CLOSED` here made a bill the
    // domain allows to be paid unpayable through this path alone.
    !isPayable -> InvoiceError.CannotPayOpenInvoice
    date < closingDate -> InvoiceError.PaymentDateBeforeClosing
    date > dueDate -> InvoiceError.PaymentDateAfterDue
    date > today -> InvoiceError.PaymentDateInFuture
    else -> null
}
