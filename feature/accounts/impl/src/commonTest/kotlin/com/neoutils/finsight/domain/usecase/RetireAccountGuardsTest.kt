package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.CardMonthFlows
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.InvoiceFlows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Delete and close are different actions, and each refuses the other's case
 * rather than quietly doing it. A use case that silently does something other
 * than its name leaves the caller — and the user reading the button — with a
 * wrong expectation, and the UI is not the safeguard.
 */
class RetireAccountGuardsTest {

    private val account = Account(id = 1, name = "Wallet", type = AccountType.ASSET)

    @Test
    fun `deleting an account with transactions is refused, not silently closed`() = runTest {
        val repository = RecordingAccountRepository()
        val useCase = DeleteAccountUseCaseImpl(
            accountRepository = repository,
            entryRepository = FakeEntries(hasEntries = true),
        )

        val error = assertIs<AccountException>(useCase(account).leftOrNull())

        assertEquals(AccountError.HAS_TRANSACTIONS, error.error)
        assertTrue(repository.deleted.isEmpty(), "nothing may be removed")
    }

    @Test
    fun `deleting the default account is refused before anything else`() = runTest {
        val repository = RecordingAccountRepository()
        val useCase = DeleteAccountUseCaseImpl(
            accountRepository = repository,
            entryRepository = FakeEntries(hasEntries = false),
        )

        val error = assertIs<AccountException>(useCase(account.copy(isDefault = true)).leftOrNull())

        assertEquals(AccountError.CANNOT_DELETE_DEFAULT, error.error)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `closing an account with a balance is refused, and nothing is written`() = runTest {
        val ledger = FakeEntries(hasEntries = true, balance = 100.0)
        val dao = RecordingAccountDao()
        val useCase = CloseAccountUseCaseImpl(accountDao = dao, entryRepository = ledger)

        val error = assertIs<AccountException>(useCase(account).leftOrNull())

        assertEquals(AccountError.HAS_BALANCE, error.error)
        assertTrue(dao.closed.isEmpty(), "the account must stay open")
        // The point of the rule: no invented transaction either.
        assertTrue(ledger.written.isEmpty(), "no write-off may be created")
    }

    @Test
    fun `closing an account with movement and no balance closes it`() = runTest {
        val dao = RecordingAccountDao()
        val useCase = CloseAccountUseCaseImpl(
            accountDao = dao,
            entryRepository = FakeEntries(hasEntries = true, balance = 0.0),
        )

        assertTrue(useCase(account).isRight())
        assertEquals(listOf(account.id), dao.closed)
    }

    @Test
    fun `closing an account that never moved is refused`() = runTest {
        val dao = RecordingAccountDao()
        val useCase = CloseAccountUseCaseImpl(
            accountDao = dao,
            entryRepository = FakeEntries(hasEntries = false, balance = 0.0),
        )

        val error = assertIs<AccountException>(useCase(account).leftOrNull())

        assertEquals(AccountError.NO_TRANSACTIONS, error.error)
        assertTrue(dao.closed.isEmpty())
    }

    @Test
    fun `deleting an account that never moved removes it`() = runTest {
        val repository = RecordingAccountRepository()
        val useCase = DeleteAccountUseCaseImpl(
            accountRepository = repository,
            entryRepository = FakeEntries(hasEntries = false),
        )

        assertTrue(useCase(account).isRight())
        assertEquals(listOf(account.id), repository.deleted)
    }
}

private class RecordingAccountRepository : IAccountRepository {
    val deleted = mutableListOf<Long>()
    override suspend fun delete(account: Account) { deleted += account.id }
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(emptyList())
    override suspend fun getAllAccounts(): List<Account> = emptyList()
    override suspend fun getAllLedgerAccounts(): List<Account> = emptyList()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(emptyList())
    override suspend fun getAccountById(accountId: Long): Account? = throw NotImplementedError()
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
}

private class FakeEntries(
    private val hasEntries: Boolean,
    private val balance: Double = 0.0,
) : IEntryRepository {
    val written = mutableListOf<Any>()
    override suspend fun hasEntries(accountId: Long): Boolean = hasEntries
    override suspend fun balance(accountId: Long): Double = balance
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
    override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
    override suspend fun invoiceFlows(invoiceId: Long): InvoiceFlows = throw NotImplementedError()
    override suspend fun cardMonthFlows(month: YearMonth): CardMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun categoryTotals(
        categoryType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long, Double> = throw NotImplementedError()
    override suspend fun categoryTotalsForInvoices(
        categoryType: AccountType,
        invoiceIds: List<Long>,
    ): Map<Long, Double> = throw NotImplementedError()
}

private class RecordingAccountDao : AccountDao {
    val closed = mutableListOf<Long>()
    override suspend fun close(id: Long) { closed += id }
    override suspend fun entryCount(accountId: Long): Int = throw NotImplementedError()
    override fun observeAllAccounts(): Flow<List<AccountEntity>> = flowOf(emptyList())
    override suspend fun getAllAccounts(): List<AccountEntity> = emptyList()
    override suspend fun getAllLedgerAccounts(): List<AccountEntity> = emptyList()
    override fun observeAllLedgerAccounts(): Flow<List<AccountEntity>> = flowOf(emptyList())
    override suspend fun getAccountById(id: Long): AccountEntity? = throw NotImplementedError()
    override suspend fun getByTypeAndName(type: AccountEntity.Type, name: String): AccountEntity? = throw NotImplementedError()
    override fun observeAccountById(id: Long): Flow<AccountEntity?> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): AccountEntity? = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<AccountEntity?> = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: AccountEntity): Long = throw NotImplementedError()
    override suspend fun update(account: AccountEntity) = throw NotImplementedError()
    override suspend fun delete(account: AccountEntity) = throw NotImplementedError()
}
