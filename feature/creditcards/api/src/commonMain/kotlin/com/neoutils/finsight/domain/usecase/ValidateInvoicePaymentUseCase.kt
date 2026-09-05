package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.LocalDate

/**
 * Whether this invoice may be paid on this date.
 *
 * **It exists so the answer is reached before the money moves.** Paying a bill is two writes — the
 * posting that takes the money out, and the invoice marked paid — and they cannot be one atomic
 * write here. So whatever refuses the payment has to refuse it *before* the first: a refusal
 * discovered between the two leaves the account short by a payment the app reports as never having
 * happened, and there is no compensating write to put it back.
 *
 * Both operations consult it — the one that posts the payment before writing anything, and the one
 * that marks the invoice paid as its own guard — so the answer given before the posting and the
 * answer given after it are the same answer, and cannot drift into disagreeing.
 *
 * It reads [Invoice.isPayable] rather than naming statuses: which ones accept a payment is that
 * property's to decide, and restating it here as "closed" once made a retroactive invoice — one the
 * ledger is perfectly willing to settle — unpayable through this path alone.
 *
 * It takes no collaborator, so it is a concrete class on the `api`: everything it needs to answer
 * arrives in the call.
 */
class ValidateInvoicePaymentUseCase {

    /**
     * @param today the date the app considers current, passed in rather than read, so that what
     * "in the future" means is the same decision the rest of the app makes.
     * @return the invoice itself when nothing stops the payment, and the reason when something does.
     * The order is the order a person would say it in — what the invoice *is*, then when it is being
     * paid — so the first thing they are told is the one that explains the rest.
     */
    operator fun invoke(
        invoice: Invoice,
        date: LocalDate,
        today: LocalDate,
    ): Either<InvoiceError, Invoice> = when {
        !invoice.isPayable -> InvoiceError.CannotPayOpenInvoice.left()
        date < invoice.closingDate -> InvoiceError.PaymentDateBeforeClosing.left()
        date > invoice.dueDate -> InvoiceError.PaymentDateAfterDue.left()
        date > today -> InvoiceError.PaymentDateInFuture.left()
        else -> invoice.right()
    }
}
