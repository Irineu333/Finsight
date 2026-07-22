@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.ExperimentalTime

class CloseInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
    private val openInvoiceUseCase: OpenInvoiceUseCase,
) {
    suspend operator fun invoke(
        invoiceId: Long,
        closedAt: LocalDate
    ): Either<InvoiceException, Invoice> = either {

        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        ensure(invoice.status != Invoice.Status.PAID) {
            InvoiceException(InvoiceError.CannotClosePaidInvoice)
        }

        ensure(invoice.status != Invoice.Status.CLOSED) {
            InvoiceException(InvoiceError.AlreadyClosed)
        }

        // Reading the predicate instead of re-enumerating around it: the pair of
        // `!=` above admits FUTURE, which `isClosable` does not and no screen offers.
        ensure(invoice.isClosable) {
            InvoiceException(InvoiceError.AlreadyClosed)
        }

        ensure(closedAt.yearMonth == invoice.closingMonth) {
            InvoiceException(InvoiceError.CannotCloseOutsideClosingMonth)
        }

        val invoiceAmount = calculateInvoiceUseCase(invoice)

        ensure(invoiceAmount >= 0) {
            InvoiceException(InvoiceError.NegativeBalance)
        }

        // A retroactive invoice used to be marked PAID here whatever it owed. The
        // ledger knows nothing about status, so nothing settled the LIABILITY legs
        // of its purchases: the debt sat in the card's balance — and in net worth —
        // for good, while the app displayed "paga". Only an invoice that owes
        // nothing can be settled by closing it; one with a balance closes like any
        // other, and is paid explicitly.
        if (invoice.status.isRetroactive && invoiceAmount == 0.0) {
            return@either payInvoiceUseCase(
                invoice = invoice,
                paidAt = closedAt,
            ).bind()
        }

        val closedInvoice = invoice.copy(
            status = Invoice.Status.CLOSED,
            closedAt = closedAt,
        ).also {
            invoiceRepository.update(it)
        }

        // A retroactive invoice belongs to a past cycle; the current one is already
        // open, and opening another would leave two OPEN invoices on the same card —
        // an invariant the whole invoice lookup assumes.
        if (!invoice.status.isRetroactive) {
            openInvoiceUseCase(
                creditCardId = invoice.creditCard.id,
                openingMonth = invoice.closingMonth
            )
        }

        if (invoiceAmount == 0.0) {
            return@either payInvoiceUseCase(
                invoice = closedInvoice,
                paidAt = closedAt,
            ).bind()
        }

        closedInvoice
    }
}
