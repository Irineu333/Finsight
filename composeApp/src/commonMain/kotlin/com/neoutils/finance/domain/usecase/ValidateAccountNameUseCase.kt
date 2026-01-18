package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.repository.IAccountRepository
import com.neoutils.finance.util.UiText

class ValidateAccountNameUseCase(
    private val repository: IAccountRepository
) {
    suspend operator fun invoke(
        name: String,
        ignoreId: Long? = null
    ): UiText? {
        if (name.isEmpty()) {
            return UiText.Raw("O nome da conta não pode ser vazio.")
        }

        if (hasDuplicateName(name, ignoreId)) {
            return UiText.Raw("Já existe uma conta com esse nome.")
        }

        return null
    }

    private suspend fun hasDuplicateName(name: String, ignoreId: Long?): Boolean {
        val accounts = repository.getAllAccounts()
        return accounts.any {
            it.name.equals(name.trim(), ignoreCase = true) && it.id != ignoreId
        }
    }
}
