package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
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

private class FakeEntries(private val hasEntries: Boolean) : IEntryRepository {
    override suspend fun hasEntries(accountId: Long): Boolean = hasEntries
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
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
