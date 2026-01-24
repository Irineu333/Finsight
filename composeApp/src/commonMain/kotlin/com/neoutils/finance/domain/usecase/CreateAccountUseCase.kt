@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.repository.IAccountRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CreateAccountUseCase(
    private val repository: IAccountRepository,
    private val validateAccountName: ValidateAccountNameUseCase,
    private val setDefaultAccount: SetDefaultAccountUseCase,
) {
    suspend operator fun invoke(
        name: String,
        isDefault: Boolean
    ): Result<Long> {

        return validateAccountName(name).mapCatching {
            repository.insert(
                Account(
                    name = name.trim(),
                    isDefault = false,
                    createdAt = Clock.System.now().toEpochMilliseconds()
                )
            )
        }.onSuccess { accountId ->
            if (isDefault) {
                setDefaultAccount(accountId)
            }
        }
    }
}
