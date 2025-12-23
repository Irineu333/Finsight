package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.AddTransactionErrors
import com.neoutils.finance.domain.exception.AddTransactionException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository

private val errors = AddTransactionErrors()

class AddTransactionUseCase(
    private val transactionRepository: ITransactionRepository,
) {
    suspend operator fun invoke(
        transaction: Transaction
    ): Result<Transaction> {

        if (transaction.target.isAccount) {

            transactionRepository.insert(transaction)

            return Result.success(transaction)
        }

        if (transaction.creditCard == null) {
            return Result.failure(AddTransactionException(errors.creditCardRequired))
        }

        if (transaction.invoice == null) {
            return Result.failure(AddTransactionException(errors.invoiceNotFound))
        }

        if (transaction.invoice.status != Invoice.Status.OPEN) {
            return Result.failure(AddTransactionException(errors.invoiceNotOpen))
        }

        transactionRepository.insert(transaction)

        return Result.success(transaction)
    }
}

