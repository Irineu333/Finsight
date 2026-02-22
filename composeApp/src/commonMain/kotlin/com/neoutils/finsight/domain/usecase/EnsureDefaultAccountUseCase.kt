package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository

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
            name = "Carteira",
            isDefault = true
        )
        val id = repository.insert(newAccount)
        return newAccount.copy(id = id)
    }
}
