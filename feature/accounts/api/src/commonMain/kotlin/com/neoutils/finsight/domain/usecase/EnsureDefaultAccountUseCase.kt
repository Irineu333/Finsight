package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.extension.localeCurrencyCode
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
 * The answer comes from the device's **region**, reduced to a currency the app
 * actually offers. It is not persisted preference yet — that is the base currency,
 * seeded once on first run; until it exists, the resolver is the answer.
 */
class EnsureDefaultAccountUseCase(
    private val repository: IAccountRepository,
    private val name: UiText = UiText.Res(Res.string.account_default_name),
    private val currency: String = CurrencyCatalog.reduce(localeCurrencyCode()),
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
                currency = currency,
                isDefault = true
            ).let {
                it.copy(
                    id = repository.insert(it)
                )
            }
        }
    }
}
