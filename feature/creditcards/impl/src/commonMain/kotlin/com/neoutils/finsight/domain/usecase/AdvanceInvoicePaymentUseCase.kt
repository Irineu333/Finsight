@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.extension.today
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Pays part of what an invoice owes, leaving its status untouched.
 *
 * The invoices that accept this are the ones still taking spending — `OPEN` and
 * `RETROACTIVE` — because only an invoice without a final figure can be paid in part.
 * `Invoice.acceptsPartialPayment` is the owner of that rule, and the guard below is what
 * makes it a permission and not merely an offer.
 *
 * **[amount] is in the card's currency and always has been**, and that is what makes the
 * ceiling below correct: `amount <= currentBillAmount` compares two figures denominated
 * the same way. When the paying account is denominated differently the caller adds
 * [paidAmount], which is what leaves the *account* — and that side carries no ceiling at
 * all, because comparing it to the invoice would be comparing two currencies.
 */
class AdvanceInvoicePaymentUseCase(
    private val writeInvoicePayment: WriteInvoicePaymentUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val clock: Clock,
) {

    private val currentDate get() = clock.today()

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
        ensure(amount > 0) {
            InvoiceException(InvoiceError.NegativeAmount)
        }

        ensure(paidAmount == null || paidAmount > 0) {
            InvoiceException(InvoiceError.NegativeAmount)
        }

        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        // Before the date window, so a refusal names the real reason: a closed invoice
        // is not refused because of *when* it is being paid but because of *what* it
        // accepts. The offer and the permission read the same predicate, so a screen
        // that starts calling this cannot inherit the permission silently.
        ensure(invoice.acceptsPartialPayment) {
            InvoiceException(InvoiceError.InvoiceNotPartiallyPayable)
        }

        ensure(date >= invoice.openingDate && date <= invoice.closingDate) {
            InvoiceException(InvoiceError.DateOutsideInvoicePeriod)
        }

        ensure(date <= currentDate) {
            InvoiceException(InvoiceError.DateInFuture)
        }

        val currentBillAmount = calculateInvoiceUseCase(invoice)

        ensure(currentBillAmount > 0.0) {
            InvoiceException(InvoiceError.InvoiceNotInDebt)
        }

        // The ceiling holds over the card's side only. The account's side is free,
        // because a limit on it would be a limit expressed in the wrong currency.
        ensure(amount <= currentBillAmount) {
            InvoiceException(InvoiceError.AmountExceedsInvoice)
        }

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
