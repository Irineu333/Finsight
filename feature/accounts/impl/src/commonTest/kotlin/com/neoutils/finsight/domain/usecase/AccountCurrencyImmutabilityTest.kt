package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The currency of an account is fixed at creation and never changes — design D12.
 *
 * The refusal is **unconditional**, and the two tests that matter are the pair: an
 * account with entries and one without are refused identically, because currency is an
 * attribute of identity and not of history. A conditional refusal would be one somebody
 * has to remember to keep correct, and this use case has no ledger dependency at all
 * with which to consult a condition — which is the mechanical proof that it reads none.
 */
class AccountCurrencyImmutabilityTest {

    private val account = Account(id = 1, name = "Nubank", currency = "BRL")

    private fun useCase(repository: RecordingAccounts) = UpdateAccountUseCase(
        repository = repository,
        validateAccountName = ValidateAccountNameUseCase(repository),
        setDefaultAccount = SetDefaultAccountUseCase(repository),
    )

    @Test
    fun `changing the currency of an account with entries is refused`() = runTest {
        val repository = RecordingAccounts(account)

        val error = assertIs<AccountException>(
            useCase(repository)(account.id) { it.copy(currency = "USD") }.leftOrNull()
        )

        assertEquals(AccountError.CURRENCY_IS_IMMUTABLE, error.error)
        assertTrue(repository.updated.isEmpty(), "nothing may be written")
    }

    /**
     * The same refusal, in the state the older, weaker rule would have allowed: no
     * entries at all. The path to fixing a wrong choice is the one the app already
     * offers — delete the empty account and create another.
     */
    @Test
    fun `changing the currency of an account with no entries is refused just the same`() = runTest {
        val repository = RecordingAccounts(account)

        val error = assertIs<AccountException>(
            useCase(repository)(account.id) { it.copy(currency = "EUR") }.leftOrNull()
        )

        assertEquals(AccountError.CURRENCY_IS_IMMUTABLE, error.error)
        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun `everything else about an account is still editable`() = runTest {
        val repository = RecordingAccounts(account)

        val updated = useCase(repository)(account.id) {
            it.copy(name = "Nubank Conta", iconKey = "bank")
        }.getOrNull()

        assertEquals("Nubank Conta", updated?.name)
        assertEquals("BRL", updated?.currency)
        assertEquals(listOf("Nubank Conta"), repository.updated.map { it.name })
    }
}

private class RecordingAccounts(private val account: Account) : IAccountRepository {
    override suspend fun hasYieldingAccount(): Boolean = false
    val updated = mutableListOf<Account>()
    override suspend fun getAccountById(accountId: Long): Account? = account.takeIf { it.id == accountId }
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = listOf(account)
    override suspend fun getAllAccounts(): List<Account> = listOf(account)
    override suspend fun update(account: Account) { updated += account }
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(listOf(account))
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(listOf(account))
    override suspend fun getAllLedgerAccounts(): List<Account> = listOf(account)
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(listOf(account))
    override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(account)
    override suspend fun getDefaultAccount(): Account? = null
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(null)
    override suspend fun getAccountCount(): Int = 1
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
}
