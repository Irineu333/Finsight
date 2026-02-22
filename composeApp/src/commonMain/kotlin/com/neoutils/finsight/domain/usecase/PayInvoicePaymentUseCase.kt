@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime

class PayInvoicePaymentUseCase(
    private val operationRepository: IOperationRepository,
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

        ensure(currentBillAmount > 0.0) {
            InvoiceException(InvoiceError.InvoiceNotInDebt)
        }

        operationRepository.createOperation(
            kind = Operation.Kind.PAYMENT,
            title = "Pagamento de Fatura",
            date = date,
            categoryId = null,
            sourceAccountId = account.id,
            targetCreditCardId = invoice.creditCard.id,
            targetInvoiceId = invoice.id,
            transactions = listOf(
                Transaction(
                    category = null,
                    title = "Pagamento de Fatura",
                    type = Transaction.Type.EXPENSE,
                    amount = currentBillAmount,
                    date = date,
                    target = Transaction.Target.ACCOUNT,
                    creditCard = invoice.creditCard,
                    invoice = invoice,
                    account = account,
                ),
                Transaction(
                    category = null,
                    title = "Pagamento de Fatura",
                    type = Transaction.Type.INCOME,
                    amount = currentBillAmount,
                    date = date,
                    target = Transaction.Target.CREDIT_CARD,
                    creditCard = invoice.creditCard,
                    invoice = invoice,
                    account = null,
                ),
            ),
        )

        payInvoiceUseCase(
            invoiceId = invoiceId,
            paidAt = date,
        ).bind()
    }
}
