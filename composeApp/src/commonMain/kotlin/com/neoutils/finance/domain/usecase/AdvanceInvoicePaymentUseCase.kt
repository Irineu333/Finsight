package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.PayInvoicePaymentErrors
import com.neoutils.finance.domain.exception.PayCreditCardBillException
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

private val errors = PayInvoicePaymentErrors()

class AdvanceInvoicePaymentUseCase(
    private val repository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase
) {
    suspend operator fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
    ): Result<Transaction> {
        if (amount <= 0) {
            return Result.failure(PayCreditCardBillException(errors.negativeAmount))
        }

        val invoice = invoiceRepository.getInvoiceById(invoiceId)
            ?: return Result.failure(PayCreditCardBillException(errors.invoiceNotFound))

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
            invoice = invoice
        ).let {
            it.copy(
                id = repository.insert(it)
            )
        }

        return Result.success(transaction)
    }
}
