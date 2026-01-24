package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.repository.IAccountRepository

class UpdateAccountUseCase(
    private val repository: IAccountRepository,
    private val validateAccountName: ValidateAccountNameUseCase,
    private val setDefaultAccount: SetDefaultAccountUseCase,
) {
    suspend operator fun invoke(
        account: Account,
        name: String,
        isDefault: Boolean
    ): Result<Unit> {
        return validateAccountName(
            name = name,
            ignoreId = account.id
        ).mapCatching {
            repository.update(
                account.copy(
                    name = name.trim(),
                    isDefault = false
                )
            )
        }.onSuccess {
            if (isDefault) {
                setDefaultAccount(account.id)
            }
        }
    }
}
