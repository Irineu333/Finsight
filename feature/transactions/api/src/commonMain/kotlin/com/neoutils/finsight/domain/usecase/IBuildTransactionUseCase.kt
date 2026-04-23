package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm

interface IBuildTransactionUseCase {
    suspend operator fun invoke(
        form: TransactionForm,
        id: Long = 0,
        operationId: Long? = null,
    ): Either<Throwable, Transaction>
}
