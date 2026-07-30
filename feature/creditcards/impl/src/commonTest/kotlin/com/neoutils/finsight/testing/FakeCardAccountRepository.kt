package com.neoutils.finsight.testing

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The chart of accounts, reduced to the one question this feature asks it: what
 * currency is a card's `LIABILITY` account denominated in (design D17). Everything
 * else throws, so a test that starts depending on it says so.
 */
internal class FakeCardAccountRepository(
    private val accountsById: Map<Long, Account> = emptyMap(),
    private val currency: String = "BRL",
) : IAccountRepository {

    override suspend fun getAccountById(accountId: Long): Account? =
        accountsById[accountId] ?: Account(
            id = accountId,
            name = "Card",
            type = AccountType.LIABILITY,
            currency = currency,
        )

    override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(null)
    override fun observeAllAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAllAccounts(): List<Account> = throw NotImplementedError()
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = throw NotImplementedError()
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}
