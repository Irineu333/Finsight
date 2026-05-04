package com.neoutils.finsight.feature.accounts.usecase

import arrow.core.Either
import com.neoutils.finsight.feature.accounts.model.Account

interface IEnsureDefaultAccountUseCase {
    suspend operator fun invoke(): Either<Throwable, Account>
}
