@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.repository.IAccountRepository
import com.neoutils.finance.util.UiText
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
        validateAccountName(name)?.let { error ->
            return Result.failure(Exception(getErrorMessage(error)))
        }

        return try {
            val accountId = repository.insert(
                Account(
                    name = name.trim(),
                    isDefault = false,
                    createdAt = Clock.System.now().toEpochMilliseconds()
                )
            )

            if (isDefault) {
                setDefaultAccount(accountId)
            }

            Result.success(accountId)
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
