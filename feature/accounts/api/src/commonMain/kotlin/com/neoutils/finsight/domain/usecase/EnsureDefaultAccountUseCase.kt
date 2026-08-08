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
 * The account a brand-new install starts with — and, with it, the first currency the
 * app ever denominates anything in.
 *
 * It **says** which currency, rather than falling into a model default, and that
 * closes a circularity: the base currency used to be "seeded when the first account
 * is created", and that account was this one, created with no currency at all and
 * landing on the ledger's `BASE_CURRENCY = "BRL"`. The base derived BRL from BRL, and
 * no user outside Brazil had a path to anything else (design D28).
 *
 * The answer is the **base currency**, which is itself resolved from the device's
 * region on the first run and persisted there (design D28). Reading the seeded value
 * rather than the resolver directly is what keeps the two from being able to disagree:
 * a device whose region changes between the app first opening and this account being
 * created would otherwise give one answer to the base and another to the account.
 */
class EnsureDefaultAccountUseCase(
    private val repository: IAccountRepository,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
    private val name: UiText = UiText.Res(Res.string.account_default_name),
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
                currency = baseCurrencyRepository.observe().value,
                isDefault = true
            ).let {
                it.copy(
                    id = repository.insert(it)
                )
            }
        }
    }
}
