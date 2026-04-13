package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.account_default_name
import com.neoutils.finsight.util.UiText

class EnsureDefaultAccountUseCase(
    private val repository: IAccountRepository,
    private val name: UiText = UiText.Res(Res.string.account_default_name)
) {
    suspend operator fun invoke(): Either<Throwable, Account> = catch {

        val existingDefault = repository.getDefaultAccount()

        if (existingDefault != null) return@catch existingDefault

        val accounts = repository.getAllAccounts()

        if (accounts.isNotEmpty()) {
            accounts
                .first()
                .copy(isDefault = true)
                .also {
                    repository.update(it)
                }
        } else {
            Account(
                name = name.asString(),
                isDefault = true
            ).let {
                it.copy(
                    id = repository.insert(it)
                )
            }
        }
    }
}
