package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.account_default_name
import com.neoutils.finsight.util.UiText

class EnsureDefaultAccountUseCase(
    private val repository: IAccountRepository,
    private val name: UiText = UiText.Res(Res.string.account_default_name)
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
            name = name.asString(),
            isDefault = true
        )
        return newAccount.copy(id = repository.insert(newAccount))
    }
}
