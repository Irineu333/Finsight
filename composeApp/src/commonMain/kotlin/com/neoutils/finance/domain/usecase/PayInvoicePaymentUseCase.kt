@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finance.domain.error.InvoiceError
import com.neoutils.finance.domain.error.InvoiceException
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime

class PayInvoicePaymentUseCase(
    private val repository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase
) {
    suspend operator fun invoke(
        invoiceId: Long,
        date: LocalDate,
        account: Account,
    ): Either<InvoiceException, Invoice> = either {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        ensure(invoice.status == Invoice.Status.CLOSED) {
            InvoiceException(InvoiceError.InvoiceNotClosed)
        }

        val currentBillAmount = calculateInvoiceUseCase(invoiceId)

        Transaction(
            category = null,
            title = null,
            type = Transaction.Type.INVOICE_PAYMENT,
            amount = currentBillAmount,
            date = date,
            target = Transaction.Target.INVOICE_PAYMENT,
            creditCard = invoice.creditCard,
            invoice = invoice,
            account = account,
        ).let { transaction ->
            transaction.copy(
                id = repository.insert(transaction)
            )
        }

        payInvoiceUseCase(
            invoiceId = invoiceId,
            paidAt = date,
        ).bind()
    }
}
