package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Declaring that an account yields — and withdrawing the declaration — changes what
 * the app *offers* and nothing else. No transaction is written, none is removed, and
 * the balance is the one it always was.
 */
class YieldDeclarationTest {

    private val account = Account(id = 1, name = "Nubank", type = AccountType.ASSET)
    private val date = LocalDate(2026, 7, 5)

    private fun updateUseCase(accounts: AccountStore) = UpdateAccountUseCase(
        repository = accounts,
        validateAccountName = ValidateAccountNameUseCase(accounts),
        setDefaultAccount = SetDefaultAccountUseCase(accounts),
    )

    @Test
    fun `declaring a yield writes no transaction`() = runTest {
        val accounts = AccountStore(account)
        val transactions = RecordingTransactionsForDeclaration()

        updateUseCase(accounts)(account.id) { it.copy(yieldsInterest = true) }

        assertTrue(accounts.rows.single().yieldsInterest)
        assertTrue(transactions.touched.isEmpty())
        assertTrue(accounts.hasYieldingAccount())
    }

    @Test
    fun `withdrawing the declaration leaves every launch untouched`() = runTest {
        val accounts = AccountStore(account.copy(yieldsInterest = true))
        val transactions = RecordingTransactionsForDeclaration()
        val categories = YieldCategoryStore()
        LaunchYieldUseCase(transactions, EnsureYieldCategoryUseCase(categories))(
            account = accounts.rows.single(),
            date = date,
            amount = 12.40,
        )
        val before = transactions.touched.toList()

        updateUseCase(accounts)(account.id) { it.copy(yieldsInterest = false) }

        assertFalse(accounts.rows.single().yieldsInterest)
        assertFalse(accounts.hasYieldingAccount())
        // The history is exactly what it was: only the affordance stopped being offered.
        assertEquals(before, transactions.touched)
        assertEquals(listOf("created"), transactions.touched)
    }
}

/** An account store that answers the yield question from the rows it holds. */
private class AccountStore(vararg seed: Account) : IAccountRepository {

    val rows = seed.toMutableList()

    override suspend fun getAccountById(accountId: Long): Account? = rows.firstOrNull { it.id == accountId }
    override suspend fun update(account: Account) {
        val index = rows.indexOfFirst { it.id == account.id }
        if (index >= 0) rows[index] = account
    }
    override suspend fun hasYieldingAccount(): Boolean = rows.any { !it.isArchived && it.yieldsInterest }
    override fun observeHasYieldingAccount(): Flow<Boolean> = flowOf(rows.any { it.yieldsInterest })
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = rows
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(rows)
    override suspend fun getAllAccounts(): List<Account> = rows
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(rows)
    override suspend fun getAllLedgerAccounts(): List<Account> = rows
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(rows)
    override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(rows.firstOrNull { it.id == accountId })
    override suspend fun getDefaultAccount(): Account? = rows.firstOrNull { it.isDefault }
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(rows.firstOrNull { it.isDefault })
    override suspend fun getAccountCount(): Int = rows.size
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}

/** Records that a transaction was written at all — the only thing this test asks. */
private class RecordingTransactionsForDeclaration : com.neoutils.finsight.domain.repository.ITransactionRepository {

    val touched = mutableListOf<String>()

    override suspend fun createTransaction(
        intent: com.neoutils.finsight.domain.model.TransactionIntent
    ): com.neoutils.finsight.domain.model.Transaction {
        touched += "created"
        return com.neoutils.finsight.domain.model.Transaction(
            id = touched.size.toLong(),
            title = intent.title,
            date = intent.date,
            entries = emptyList(),
        )
    }

    override suspend fun createTransactions(
        intents: List<com.neoutils.finsight.domain.model.TransactionIntent>
    ) = intents.map { createTransaction(it) }

    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        leg: com.neoutils.finsight.domain.model.TransactionLeg,
        contra: com.neoutils.finsight.domain.model.ContraLeg?,
    ) { touched += "updated" }

    override suspend fun deleteTransactionById(id: Long) { touched += "deleted" }
    override suspend fun deleteTransactionsByIds(ids: List<Long>) { touched += "deleted" }
    override fun observeTransactionsBy(
        date: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): Flow<List<com.neoutils.finsight.domain.model.Transaction>> = flowOf(emptyList())
    override fun observeAllTransactions(): Flow<List<com.neoutils.finsight.domain.model.Transaction>> =
        throw NotImplementedError()
    override fun observeTransactionById(id: Long): Flow<com.neoutils.finsight.domain.model.Transaction?> =
        throw NotImplementedError()
    override suspend fun getAllTransactions(): List<com.neoutils.finsight.domain.model.Transaction> =
        throw NotImplementedError()
    override suspend fun getTransactionById(id: Long): com.neoutils.finsight.domain.model.Transaction? =
        throw NotImplementedError()
}
