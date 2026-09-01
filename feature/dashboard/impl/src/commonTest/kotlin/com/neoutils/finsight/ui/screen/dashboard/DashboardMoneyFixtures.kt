package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.extension.ConsolidatedAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * The reducer over an archive with no rate. Every figure the dashboard builds here is
 * mono-currency, so it comes out as its single exact term — which is what the assertions
 * read through [value].
 */
internal fun reducer() = ConsolidateMoneyUseCase(
    baseCurrencyRepository = FakeBaseCurrencyRepository(),
    exchangeRateRepository = FakeExchangeRateRepository(),
    getAccountCurrencies = FakeAccountCurrencies(),
)

internal class FakeAccountCurrencies(
    private val inUse: List<String> = listOf("BRL"),
) : GetAccountCurrenciesUseCase {
    override suspend fun invoke() = AccountCurrencies(inUse = inUse, ofDefaultAccount = inUse.firstOrNull())
}

/** The one value of a mono-currency figure. */
internal val ConsolidatedAmount.value: Double get() = terms.single().value

internal class FakeBaseCurrencyRepository(base: String = "BRL") : IBaseCurrencyRepository {
    private val flow = MutableStateFlow(base)
    override fun observe(): StateFlow<String> = flow
    override suspend fun set(code: String) { flow.value = code }
}

internal class FakeExchangeRateRepository : IExchangeRateRepository {
    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()
    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
    override suspend fun save(rate: ExchangeRate) = Unit
    override suspend fun remove(rate: ExchangeRate) = Unit
    override suspend fun countNaming(currency: String) = 0
}

/**
 * Answers only what denominating a card's or a recurring's figure asks of it: which
 * account a given id names. Everything else is outside what the builder does.
 */
internal class FakeAccountRepository(
    private val accounts: List<Account> = emptyList(),
) : IAccountRepository {
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAccountById(accountId: Long): Account? =
        accounts.firstOrNull { it.id == accountId }

    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllAccounts(): List<Account> = accounts
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllLedgerAccounts(): List<Account> = accounts
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun observeAccountById(accountId: Long): Flow<Account?> =
        flowOf(accounts.firstOrNull { it.id == accountId })

    override suspend fun getDefaultAccount(): Account? = accounts.firstOrNull { it.isDefault }
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(getDefault())
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()

    private fun getDefault() = accounts.firstOrNull { it.isDefault }
}
