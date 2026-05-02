package com.neoutils.finsight.feature.installments.usecase

import arrow.core.Either
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.model.form.TransactionForm
interface IAddInstallmentUseCase {
    suspend operator fun invoke(
        form: TransactionForm,
        installments: Int,
    ): Either<Throwable, List<Transaction>>
}
