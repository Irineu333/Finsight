@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.error.PayInvoicePaymentErrors
import com.neoutils.finance.domain.exception.PayCreditCardBillException
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime

private val errors = PayInvoicePaymentErrors()

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
    ): Result<Invoice> {

        val invoice = invoiceRepository.getInvoiceById(invoiceId)
            ?: return Result.failure(PayCreditCardBillException(errors.invoiceNotFound))

        if (invoice.status != Invoice.Status.CLOSED) {
            return Result.failure(PayCreditCardBillException(errors.invoiceNotClosed))
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

        return payInvoiceUseCase(
            invoiceId = invoiceId,
            paidAt = date,
        )
    }
}
