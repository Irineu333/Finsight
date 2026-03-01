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
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class AdvanceInvoicePaymentUseCase(
    private val operationRepository: IOperationRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase
) {
    suspend operator fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        account: Account,
    ): Either<Throwable, Operation> = either {
        ensure(amount > 0) {
            InvoiceException(InvoiceError.NegativeAmount)
        }

        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        ensure(date >= invoice.openingDate && date <= invoice.closingDate) {
            InvoiceException(InvoiceError.DateOutsideInvoicePeriod)
        }

        ensure(date <= currentDate) {
            InvoiceException(InvoiceError.DateInFuture)
        }

        val currentBillAmount = calculateInvoiceUseCase(invoiceId)

        ensure(currentBillAmount > 0.0) {
            InvoiceException(InvoiceError.InvoiceNotInDebt)
        }

        ensure(amount <= currentBillAmount) {
            InvoiceException(InvoiceError.AmountExceedsInvoice)
        }
        
        catch {
            operationRepository.createOperation(
                kind = Operation.Kind.PAYMENT,
                title = null,
                date = date,
                categoryId = null,
                sourceAccountId = account.id,
                targetCreditCardId = invoice.creditCard.id,
                targetInvoiceId = invoice.id,
                transactions = listOf(
                    Transaction(
                        category = null,
                        title = null,
                        type = Transaction.Type.EXPENSE,
                        amount = amount,
                        date = date,
                        target = Transaction.Target.ACCOUNT,
                        creditCard = invoice.creditCard,
                        invoice = invoice,
                        account = account,
                    ),
                    Transaction(
                        category = null,
                        title = null,
                        type = Transaction.Type.INCOME,
                        amount = amount,
                        date = date,
                        target = Transaction.Target.CREDIT_CARD,
                        creditCard = invoice.creditCard,
                        invoice = invoice,
                        account = null,
                    ),
                ),
            )
        }.bind()
    }
}
