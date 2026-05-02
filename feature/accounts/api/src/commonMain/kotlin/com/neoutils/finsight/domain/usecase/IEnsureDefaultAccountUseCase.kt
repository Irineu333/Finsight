package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.core.domain.model.Account

interface IEnsureDefaultAccountUseCase {
    suspend operator fun invoke(): Either<Throwable, Account>
}
