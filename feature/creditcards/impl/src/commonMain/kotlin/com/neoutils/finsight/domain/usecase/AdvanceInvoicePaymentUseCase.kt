package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate

/**
 * Pays part of what an invoice owes, leaving its status untouched.
 *
 * The invoices that accept this are the ones still taking spending — `OPEN` and
 * `RETROACTIVE` — because only an invoice without a final figure can be paid in part.
 * `Invoice.acceptsPartialPayment` is the owner of that rule, and
 * [ValidateInvoicePaymentUseCase] is what makes it a permission and not merely an offer:
 * it owns every rule this operation is admissible by, and [UpdateAdvanceInvoicePaymentUseCase]
 * reads the same one, so registering a payment and correcting one cannot drift apart.
 *
 * **[amount] is in the card's currency and always has been**, and that is what makes the
 * ceiling correct: it is compared to what the invoice owes, two figures denominated the
 * same way. When the paying account is denominated differently the caller adds
 * [paidAmount], which is what leaves the *account* — and that side carries no ceiling at
 * all, because comparing it to the invoice would be comparing two currencies.
 */
class AdvanceInvoicePaymentUseCase(
    private val writeInvoicePayment: WriteInvoicePaymentUseCase,
    private val validateInvoicePayment: ValidateInvoicePaymentUseCase,
) {

    /**
     * @param amount how much of the invoice is being settled, in the **card's** currency.
     * @param paidAmount what leaves [account], when it is denominated differently.
     * `null` is the same-currency case, unchanged.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        account: Account,
        paidAmount: Double? = null,
    ): Either<Throwable, Transaction> = either {
        // No operation to leave out of the ceiling: this one does not exist yet.
        val (invoice) = validateInvoicePayment(
            invoiceId = invoiceId,
            amount = amount,
            date = date,
            paidAmount = paidAmount,
        ).mapLeft { InvoiceException(it) }.bind()

        catch {
            writeInvoicePayment(
                invoice = invoice,
                account = account,
                leaving = paidAmount ?: amount,
                settling = amount,
                date = date,
            )
        }.bind()
    }
}
