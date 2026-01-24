package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.ValidateAccountNameErrors
import com.neoutils.finance.domain.exception.AccountException
import com.neoutils.finance.domain.repository.IAccountRepository

private val errors = ValidateAccountNameErrors()

class ValidateAccountNameUseCase(
    private val repository: IAccountRepository
) {
    suspend operator fun invoke(
        name: String,
        ignoreId: Long? = null
    ): Result<String> {
        if (name.isEmpty()) {
            return Result.failure(AccountException(errors.nameRequired))
        }

        if (hasDuplicateName(name, ignoreId)) {
            return Result.failure(AccountException(errors.nameAlreadyExists))
        }

        return Result.success(name)
    }

    private suspend fun hasDuplicateName(name: String, ignoreId: Long?): Boolean {
        val accounts = repository.getAllAccounts()
        return accounts.any {
            it.name.equals(name.trim(), ignoreCase = true) && it.id != ignoreId
        }
    }
}
