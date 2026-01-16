package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.repository.IAccountRepository

class EnsureDefaultAccountUseCase(
    private val repository: IAccountRepository
) {
    suspend operator fun invoke(): Account {
        val existingDefault = repository.getDefaultAccount()
        if (existingDefault != null) {
            return existingDefault
        }

        val accounts = repository.getAllAccounts()
        if (accounts.isNotEmpty()) {
            val firstAccount = accounts.first()
            val updatedAccount = firstAccount.copy(isDefault = true)
            repository.update(updatedAccount)
            return updatedAccount
        }

        val newAccount = Account(
            name = "Principal",
            isDefault = true
        )
        val id = repository.insert(newAccount)
        return newAccount.copy(id = id)
    }
}
