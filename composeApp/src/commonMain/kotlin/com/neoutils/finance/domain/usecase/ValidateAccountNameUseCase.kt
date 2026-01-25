package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finance.domain.error.AccountError
import com.neoutils.finance.domain.repository.IAccountRepository

class ValidateAccountNameUseCase(
    private val repository: IAccountRepository
) {
    suspend operator fun invoke(
        name: String,
        ignoreId: Long? = null
    ): Either<AccountError, String> {
        if (name.isEmpty()) {
            return AccountError.EMPTY_NAME.left()
        }

        if (hasDuplicateName(name, ignoreId)) {
            return AccountError.ALREADY_EXIST.left()
        }

        return name.right()
    }

    private suspend fun hasDuplicateName(name: String, ignoreId: Long?): Boolean {
        // TODO: improve this
        return repository.getAllAccounts().any { account ->
            account.name.equals(name.trim(), ignoreCase = true) &&
                    account.id != ignoreId
        }
    }
}
