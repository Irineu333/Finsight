@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.PayInvoicePaymentErrors
import com.neoutils.finance.domain.exception.PayCreditCardBillException
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val errors = PayInvoicePaymentErrors()

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
    ): Result<Transaction> {
        if (amount <= 0) {
            return Result.failure(PayCreditCardBillException(errors.negativeAmount))
        }

        val invoice = invoiceRepository.getInvoiceById(invoiceId)
            ?: return Result.failure(PayCreditCardBillException(errors.invoiceNotFound))

        if (date < invoice.openingDate || date > invoice.closingDate) {
            return Result.failure(PayCreditCardBillException(errors.dateOutsideInvoicePeriod))
        }

        if (date > currentDate) {
            return Result.failure(PayCreditCardBillException(errors.dateInFuture))
        }

        val currentBillAmount = calculateInvoiceUseCase(invoiceId)

        if (amount > currentBillAmount) {
            return Result.failure(PayCreditCardBillException(errors.amountExceedsInvoice))
        }

        val transaction = Transaction(
            category = null,
            title = null,
            type = Transaction.Type.ADVANCE_PAYMENT,
            amount = amount,
            date = date,
            target = Transaction.Target.INVOICE_PAYMENT,
            creditCard = invoice.creditCard,
            invoice = invoice,
            account = account,
        ).let {
            it.copy(
                id = repository.insert(it)
            )
        }

        return Result.success(transaction)
    }
}
