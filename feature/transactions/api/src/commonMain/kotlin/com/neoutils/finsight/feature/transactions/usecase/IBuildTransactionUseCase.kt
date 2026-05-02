package com.neoutils.finsight.feature.transactions.usecase

import arrow.core.Either
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.form.TransactionForm

interface IBuildTransactionUseCase {
    suspend operator fun invoke(
        form: TransactionForm,
        id: Long = 0,
        operationId: Long? = null,
    ): Either<Throwable, Transaction>
}
