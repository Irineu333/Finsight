package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.AddTransactionErrors
import com.neoutils.finance.domain.exception.AddTransactionException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.model.form.TransactionForm

private val errors = AddTransactionErrors()

class AddTransactionUseCase(
    private val transactionRepository: ITransactionRepository,
) {
    suspend operator fun invoke(
        form: TransactionForm
    ): Result<Transaction> {

        if (form.target.isAccount) {
            return Result.success(
                form.build().let { transaction ->
                    transaction.copy(
                        id = transactionRepository.insert(transaction)
                    )
                }
            )
        }

        if (form.creditCard == null) {
            return Result.failure(AddTransactionException(errors.creditCardRequired))
        }

        if (form.invoice == null) {
            return Result.failure(AddTransactionException(errors.invoiceNotFound))
        }

        if (form.invoice.status != Invoice.Status.OPEN) {
            return Result.failure(AddTransactionException(errors.invoiceNotOpen))
        }

        return Result.success(
            form.build().let { transaction ->
                transaction.copy(
                    id = transactionRepository.insert(transaction)
                )
            }
        )
    }
}

