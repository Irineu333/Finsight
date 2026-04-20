package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.repository.IAccountRepository

class SetDefaultAccountUseCase(
    private val repository: IAccountRepository
) {
    suspend operator fun invoke(accountId: Long): Either<Throwable, Unit> = catch {
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
