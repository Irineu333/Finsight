@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finance.domain.error.InvoiceError
import com.neoutils.finance.domain.error.InvoiceException
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class AdvanceInvoicePaymentUseCase(
    private val repository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase
) {
    suspend operator fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        account: Account,
    ): Either<Throwable, Transaction> = either {
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

        ensure(amount <= currentBillAmount) {
            InvoiceException(InvoiceError.AmountExceedsInvoice)
        }
        
        Transaction(
            category = null,
            title = null,
            type = Transaction.Type.ADVANCE_PAYMENT,
            amount = amount,
            date = date,
            target = Transaction.Target.INVOICE_PAYMENT,
            creditCard = invoice.creditCard,
            invoice = invoice,
            account = account,
        ).let { transaction ->
            catch {
               transaction.copy(
                   id = repository.insert(transaction)
               )
           }
        }.bind()
    }
}
