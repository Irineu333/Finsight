package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.FeatureIconCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which icon a new account form opens on.
 *
 * The suggestion walks the account catalog in order and stops at the first icon no
 * open account uses — so the catalog's order is behaviour, and these tests are what
 * says so. It is a convenience, never a guarantee: with everything taken it falls
 * back to the default and lets the repetition happen.
 */
class SuggestAccountIconUseCaseTest {

    private fun useCase(vararg accounts: Account) =
        SuggestAccountIconUseCaseImpl(StoredAccounts(accounts.toList()))

    private fun account(id: Long, iconKey: String, isArchived: Boolean = false) = Account(
        id = id,
        name = "Account $id",
        currency = "BRL",
        iconKey = iconKey,
        isArchived = isArchived,
    )

    @Test
    fun `a clean install is suggested the first icon of the catalog`() = runTest {
        assertEquals(FeatureIconCatalog.accounts.first(), useCase()())
    }

    @Test
    fun `the first icon taken moves the suggestion to the second`() = runTest {
        val (first, second) = FeatureIconCatalog.accounts

        // Not merely "some free icon": the order of the catalog is the order of
        // preference, and reordering it changes what the user is offered.
        assertEquals(second, useCase(account(1, first.key))())
    }

    @Test
    fun `an archived account holds no icon`() = runTest {
        val first = FeatureIconCatalog.accounts.first()

        // It appears in no active listing and no selector, so it competes with
        // nothing — its icon goes back to being suggestible, in its catalog position.
        assertEquals(first, useCase(account(1, first.key, isArchived = true))())
    }

    @Test
    fun `an exhausted catalog falls back to the default account icon`() = runTest {
        val accounts = FeatureIconCatalog.accounts
            .mapIndexed { index, icon -> account(index + 1L, icon.key) }
            .toTypedArray()

        assertEquals(AppIcon.WALLET, useCase(*accounts)())
    }

    @Test
    fun `an unknown stored key eliminates no icon of the catalog`() = runTest {
        // The comparison is by the persisted key, so a value no `AppIcon` answers to
        // must not be mistaken for one — least of all for `DEFAULT`, which `fromKey`
        // would resolve it to.
        assertEquals(FeatureIconCatalog.accounts.first(), useCase(account(1, "an_icon_we_removed"))())
    }
}

private class StoredAccounts(private val accounts: List<Account>) : IAccountRepository {
    override suspend fun getAllAccounts(): List<Account> = accounts.filterNot { it.isArchived }
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(accounts.filterNot { it.isArchived })
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllLedgerAccounts(): List<Account> = accounts
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAccountById(accountId: Long): Account? = accounts.firstOrNull { it.id == accountId }
    override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(getAccountByIdOrNull(accountId))
    override suspend fun getDefaultAccount(): Account? = null
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(null)
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun hasYieldingAccount(): Boolean = accounts.any { it.yieldsInterest }
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()

    private fun getAccountByIdOrNull(accountId: Long) = accounts.firstOrNull { it.id == accountId }
}
