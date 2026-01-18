package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.repository.IAccountRepository
import com.neoutils.finance.util.UiText

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
        validateAccountName(name, ignoreId = account.id)?.let { error ->
            return Result.failure(Exception(getErrorMessage(error)))
        }

        if (account.isDefault && !isDefault) {
            return Result.failure(
                IllegalStateException("Não é possível remover o status de padrão de uma conta que já é padrão.")
            )
        }

        return try {
            repository.update(
                account.copy(
                    name = name.trim(),
                    isDefault = false
                )
            )

            if (isDefault) {
                setDefaultAccount(account.id)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getErrorMessage(uiText: UiText): String {
        return when (uiText) {
            is UiText.Raw -> uiText.value
            else -> "Erro ao validar nome da conta"
        }
    }
}
