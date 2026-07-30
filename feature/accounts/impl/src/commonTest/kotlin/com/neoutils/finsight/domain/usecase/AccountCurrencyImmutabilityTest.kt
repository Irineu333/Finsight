package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The currency of an account is fixed at creation and never changes — and the refusal
 * **consults nothing** (design D12).
 *
 * The two cases below are the same case, deliberately: an account with entries and one without
 * are refused identically, and the repository is never asked whether it has any. A conditional
 * refusal would be a refusal someone has to remember to keep correct, and the rule is not about
 * history at all — the currency is an attribute of identity, in the same degree as the type.
 *
 * The rule lives here rather than only in the form because the form is where it is *offered*,
 * not where it is true. Leaving it to the UI is the inversion this project refuses.
 */
class AccountCurrencyImmutabilityTest {

    private val account = Account(id = 1, name = "Wallet", type = AccountType.ASSET, currency = "BRL")

    @Test
    fun `changing the currency of an account with entries is refused`() = runTest {
        val repository = RecordingAccounts(account)

        val error = assertIs<AccountException>(
            useCase(repository)(accountId = account.id) { it.copy(currency = "USD") }.leftOrNull()
        )

        assertEquals(AccountError.CURRENCY_IMMUTABLE, error.error)
        assertTrue(repository.updated.isEmpty(), "nothing may be written")
    }

    @Test
    fun `changing the currency of an account without entries is refused just the same`() = runTest {
        // No `hasEntries` anywhere in this test, and that absence is the assertion: the
        // correction for a currency chosen by mistake is to delete the unused account and
        // create another, with the action the app already offers.
        val repository = RecordingAccounts(account)

        val error = assertIs<AccountException>(
            useCase(repository)(accountId = account.id) { it.copy(currency = "EUR") }.leftOrNull()
        )

        assertEquals(AccountError.CURRENCY_IMMUTABLE, error.error)
        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun `an update that leaves the currency alone goes through`() = runTest {
        val repository = RecordingAccounts(account)

        val updated = useCase(repository)(accountId = account.id) { it.copy(name = "Nubank") }

        assertTrue(updated.isRight())
        assertEquals(listOf("Nubank"), repository.updated.map { it.name })
        assertEquals(listOf("BRL"), repository.updated.map { it.currency })
    }

    private fun useCase(repository: IAccountRepository) = UpdateAccountUseCase(
        repository = repository,
        validateAccountName = ValidateAccountNameUseCase(repository = repository),
        setDefaultAccount = SetDefaultAccountUseCase(repository = repository),
    )

    private class RecordingAccounts(private val account: Account) : IAccountRepository {
        val updated = mutableListOf<Account>()

        override suspend fun getAccountById(accountId: Long): Account? =
            account.takeIf { it.id == accountId }

        override suspend fun update(account: Account) {
            updated += account
        }

        override suspend fun getAllAccounts(): List<Account> = listOf(account)
        override fun observeAllAccounts(): Flow<List<Account>> = flowOf(listOf(account))
        override suspend fun getAllAccountsIncludingClosed(): List<Account> = listOf(account)
        override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(listOf(account))
        override suspend fun getAllLedgerAccounts(): List<Account> = listOf(account)
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(listOf(account))
        override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(account)
        override suspend fun getDefaultAccount(): Account? = null
        override fun observeDefaultAccount(): Flow<Account?> = flowOf(null)
        override suspend fun getAccountCount(): Int = 1
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
        override suspend fun reopen(accountId: Long) = throw NotImplementedError()
    }
}
