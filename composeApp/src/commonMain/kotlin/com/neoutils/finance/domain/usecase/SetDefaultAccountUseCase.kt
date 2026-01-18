package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.repository.IAccountRepository

class SetDefaultAccountUseCase(
    private val repository: IAccountRepository
) {
    suspend operator fun invoke(accountId: Long) {
        val accounts = repository.getAllAccounts()

        accounts.forEach { account ->
            if (account.id == accountId && !account.isDefault) {
                repository.update(account.copy(isDefault = true))
            } else if (account.id != accountId && account.isDefault) {
                repository.update(account.copy(isDefault = false))
            }
        }
    }
}
