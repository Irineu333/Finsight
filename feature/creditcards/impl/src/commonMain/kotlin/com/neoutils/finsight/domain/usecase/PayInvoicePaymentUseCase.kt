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
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.OperationIntent
import com.neoutils.finsight.domain.model.OperationLeg
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
            OperationIntent(
                title = null,
                date = date,
                legs = listOf(
                    OperationLeg(
                        type = TransactionType.EXPENSE,
                        amount = currentBillAmount,
                        creditCard = invoice.creditCard,
                        invoice = invoice,
                        account = account,
                    ),
                    OperationLeg(
                        type = TransactionType.INCOME,
                        amount = currentBillAmount,
                        creditCard = invoice.creditCard,
                        invoice = invoice,
                    ),
                ),
            )
        )

        payInvoiceUseCase(
            invoiceId = invoiceId,
            paidAt = date,
        ).bind()
    }
}
