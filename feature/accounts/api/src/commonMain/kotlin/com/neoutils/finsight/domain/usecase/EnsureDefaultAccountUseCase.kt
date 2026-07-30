package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.account_default_name
import com.neoutils.finsight.util.UiText

/**
 * The account the app creates for a user who has none.
 *
 * It names a currency because nothing else can: the model has no default, deliberately, so
 * that no account comes into being without someone deciding what it holds. The decision
 * here is the currency this user reads totals in — resolved from the device's locale on
 * first run, which is as close as the app can get to asking without a form.
 */
class EnsureDefaultAccountUseCase(
    private val repository: IAccountRepository,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
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
                currency = baseCurrencyRepository.current(),
                isDefault = true
            ).let {
                it.copy(
                    id = repository.insert(it)
                )
            }
        }
    }
}
