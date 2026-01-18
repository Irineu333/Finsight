package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.repository.IAccountRepository

class DeleteAccountUseCase(
    private val repository: IAccountRepository
) {
    suspend operator fun invoke(account: Account): Result<Unit> {
        if (account.isDefault) {
            return Result.failure(
                IllegalStateException("Não é possível excluir a conta padrão.")
            )
        }

        return try {
            repository.delete(account)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
