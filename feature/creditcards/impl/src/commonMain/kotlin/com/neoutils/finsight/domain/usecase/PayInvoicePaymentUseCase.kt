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
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime

/**
 * Pays a closed invoice in full.
 *
 * **The paying account may be in another currency, and then the caller states what
 * leaves it** — the invoice's own side stays exactly what is owed, in the card's
 * currency, because that is a fact and not a choice. No rate is a parameter: it is the
 * quotient of the two ends, derived afterwards (design D6). The write boundary posts the
 * residue of each currency to that currency's conversion account, **without** the
 * invoice's dimension: the exchange result does not belong to the invoice, and copying
 * the dimension onto it would have the whole transaction refused (design D15).
 */
class PayInvoicePaymentUseCase(
    private val writeInvoicePayment: WriteInvoicePaymentUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
) {
    /**
     * @param paidAmount what leaves [account], when it is not what the invoice owes.
     * `null` is the same-currency case and is byte-identical to what it was.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        date: LocalDate,
        account: Account,
        paidAmount: Double? = null,
        // Throwable, not InvoiceException: `createTransaction` throws from the write
        // boundary (e.g. ClosedAccountException if the paying account is archived
        // mid-flight), and `either {}` does not intercept a thrown exception — only a
        // Raise. Left untyped it escaped the Either and could crash the caller.
        // Wrapping it in `catch {}.bind()` — as AdvanceInvoicePaymentUseCase already
        // does — turns it into the Left the ViewModel maps to a message.
    ): Either<Throwable, Invoice> = either {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        // The domain owns which invoices are discharged by paying them; enumerating
        // the status here would be a second copy of that rule.
        ensure(invoice.acceptsFullSettlement) {
            InvoiceException(InvoiceError.InvoiceNotClosed)
        }

        val currentBillAmount = calculateInvoiceUseCase(invoice)

        ensure(currentBillAmount > 0.0) {
            InvoiceException(InvoiceError.InvoiceNotInDebt)
        }

        // What the invoice owes is not negotiable; what the account gives up is, and
        // only when the two are denominated differently.
        val leaving = paidAmount ?: currentBillAmount

        ensure(leaving > 0.0) {
            InvoiceException(InvoiceError.InvoiceNotInDebt)
        }

        catch {
            writeInvoicePayment(
                invoice = invoice,
                account = account,
                leaving = leaving,
                settling = currentBillAmount,
                date = date,
            )
        }.bind()

        payInvoiceUseCase(
            invoiceId = invoiceId,
            paidAt = date,
        ).bind()
    }
}
