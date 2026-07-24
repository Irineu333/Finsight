package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.form.TransactionForm

interface BuildTransactionUseCase {
    /** Normalizes and validates the form into the intent the ledger can write. */
    suspend operator fun invoke(form: TransactionForm): Either<Throwable, TransactionIntent>
}
