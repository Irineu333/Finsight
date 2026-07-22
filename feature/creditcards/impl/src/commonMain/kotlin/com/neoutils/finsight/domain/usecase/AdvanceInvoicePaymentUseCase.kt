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
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class AdvanceInvoicePaymentUseCase(
    private val transactionRepository: ITransactionRepository,
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

        val currentBillAmount = calculateInvoiceUseCase(invoice)

        ensure(currentBillAmount > 0.0) {
            InvoiceException(InvoiceError.InvoiceNotInDebt)
        }

        ensure(amount <= currentBillAmount) {
            InvoiceException(InvoiceError.AmountExceedsInvoice)
        }
        
        catch {
            transactionRepository.createTransaction(
                TransactionIntent(
                    title = null,
                    date = date,
                    legs = listOf(
                        TransactionLeg(
                            type = TransactionType.EXPENSE,
                            amount = amount,
                            creditCard = invoice.creditCard,
                            invoice = invoice,
                            account = account,
                        ),
                        TransactionLeg(
                            type = TransactionType.INCOME,
                            amount = amount,
                            creditCard = invoice.creditCard,
                            invoice = invoice,
                        ),
                    ),
                )
            )
        }.bind()
    }
}
