package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

/**
 * Correcting a partial invoice payment that is already registered, in place.
 *
 * It is the counterpart of [AdvanceInvoicePaymentUseCase] and shares its rules wholesale
 * — [ValidateInvoicePaymentUseCase] owns them, and a payment is no more or less
 * admissible for having been written once already. What differs is the write: the legs
 * of an existing operation are rewritten rather than created, so the operation keeps its
 * identity instead of becoming a new one.
 *
 * **The mode is not redecided here.** Correcting a partial payment is reaffirming a
 * partial payment, which is why the validator's `acceptsPartialPayment` is inherited
 * rather than relaxed: an invoice that stopped taking spending refuses the correction
 * too, whether or not a screen offers it. Nothing on this path marks an invoice `PAID` —
 * that belongs to the payment that discharges one, and this is not it.
 *
 * Crossing currencies takes no branch here either. The intent arrives at the boundary
 * incomplete and is completed there, conversion legs and all, exactly as on creation.
 */
class UpdateAdvanceInvoicePaymentUseCase(
    private val writeInvoicePayment: WriteInvoicePaymentUseCase,
    private val validateInvoicePayment: ValidateInvoicePaymentUseCase,
    private val transactionRepository: ITransactionRepository,
) {
    /**
     * @param amount how much of the invoice is being settled, in the **card's** currency.
     * @param paidAmount what leaves [account], when it is denominated differently.
     * `null` is the same-currency case.
     */
    suspend operator fun invoke(
        transactionId: Long,
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        account: Account,
        paidAmount: Double? = null,
    ): Either<Throwable, Unit> = either {
        // The operation leaves its own contribution out of the ceiling: it already
        // reduced what the invoice owes, and a ceiling counting it would refuse the
        // correction that raises the figure. On an invoice it never touched — the
        // correction that switched invoices — there is nothing to leave out.
        val (invoice) = validateInvoicePayment(
            invoiceId = invoiceId,
            amount = amount,
            date = date,
            paidAmount = paidAmount,
            excluding = transactionId,
        ).mapLeft { InvoiceException(it) }.bind()

        // Read to refuse correcting an operation that is not there — and for the one
        // thing carried forward from it: the title, which this form does not exhibit
        // and therefore must not erase.
        val transaction = transactionRepository.getTransactionById(transactionId)

        ensureNotNull(transaction) { InvoiceException(InvoiceError.NotFound) }

        catch {
            writeInvoicePayment.rewrite(
                transactionId = transaction.id,
                title = transaction.title,
                invoice = invoice,
                account = account,
                leaving = paidAmount ?: amount,
                settling = amount,
                date = date,
            )
        }.bind()
    }
}
