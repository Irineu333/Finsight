@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.creditCards.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.feature.creditCards.error.InvoiceError
import com.neoutils.finsight.feature.creditCards.exception.InvoiceException
import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.core.domain.model.Transaction
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime

class PayInvoicePaymentUseCase(
    private val operationRepository: IOperationRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
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

        val creditCard = requireNotNull(
            creditCardRepository.getCreditCardById(invoice.creditCardId)
        ) { "CreditCard ${invoice.creditCardId} not found" }

        operationRepository.createOperation(
            kind = Operation.Kind.PAYMENT,
            title = null,
            date = date,
            categoryId = null,
            sourceAccountId = account.id,
            targetCreditCardId = invoice.creditCardId,
            targetInvoiceId = invoice.id,
            transactions = listOf(
                Transaction(
                    categoryId = null,
                    title = null,
                    type = Transaction.Type.EXPENSE,
                    amount = currentBillAmount,
                    date = date,
                    target = Transaction.Target.ACCOUNT,
                    creditCardId = creditCard.id,
                    invoiceId = invoice.id,
                    accountId = account.id,
                ),
                Transaction(
                    categoryId = null,
                    title = null,
                    type = Transaction.Type.INCOME,
                    amount = currentBillAmount,
                    date = date,
                    target = Transaction.Target.CREDIT_CARD,
                    creditCardId = creditCard.id,
                    invoiceId = invoice.id,
                    accountId = null,
                ),
            ),
        )

        payInvoiceUseCase(
            invoiceId = invoiceId,
            paidAt = date,
        ).bind()
    }
}
