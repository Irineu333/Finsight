package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.form.TransactionForm

interface AddInstallmentUseCase {
    suspend operator fun invoke(
        form: TransactionForm,
        installments: Int,
    ): Either<Throwable, List<Operation>>
}
