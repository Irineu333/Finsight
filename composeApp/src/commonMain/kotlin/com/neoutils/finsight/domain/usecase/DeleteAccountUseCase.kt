package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository

class DeleteAccountUseCase(
    private val repository: IAccountRepository
) {
    suspend operator fun invoke(
        account: Account
    ): Either<AccountException, Unit> = either {
        ensure(!account.isDefault) {
            AccountException(AccountError.CANNOT_DELETE_DEFAULT)
        }

        repository.delete(account)
    }
}
