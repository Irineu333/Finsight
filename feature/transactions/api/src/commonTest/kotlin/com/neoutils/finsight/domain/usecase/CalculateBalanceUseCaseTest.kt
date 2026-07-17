package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/** The in-memory form under test never touches the ledger, so every read throws. */
private fun throwingEntryRepository() = object : IEntryRepository {
    override suspend fun getEntriesByOperation(operationId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByOperation(operationId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
    override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun categoryTotals(categoryType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
    override suspend fun categoryTotalsForInvoices(categoryType: AccountType, invoiceIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
}

/**
 * Characterizes the in-memory (CAP-2) form of [CalculateBalanceUseCase]: Σ signed
 * cents of the account legs up to and including a month. Task 4.3 removes this form,
 * leaving only the ledger-backed `balanceUpTo`, which must produce the same figures.
 */
class CalculateBalanceUseCaseTest {

    private val useCase = CalculateBalanceUseCase(entryRepository = throwingEntryRepository())
    private val accountA = Account(id = 1, name = "A", type = AccountType.ASSET)
    private val accountB = Account(id = 2, name = "B", type = AccountType.ASSET)
    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)

    private fun accountLeg(type: Transaction.Type, amount: Double, month: Int, account: Account) = Transaction(
        type = type, amount = amount, title = null, date = LocalDate(2026, month, 10), account = account,
    )

    private val transactions = listOf(
        accountLeg(Transaction.Type.INCOME, 100.0, month = 1, account = accountA),
        accountLeg(Transaction.Type.EXPENSE, 30.0, month = 2, account = accountA),
        accountLeg(Transaction.Type.ADJUSTMENT, 40.0, month = 3, account = accountA),
        accountLeg(Transaction.Type.EXPENSE, 50.0, month = 4, account = accountA), // future → excluded
        accountLeg(Transaction.Type.INCOME, 20.0, month = 2, account = accountB),
        // A pure card leg is excluded by the account-target filter.
        Transaction(type = Transaction.Type.EXPENSE, amount = 999.0, title = null, date = LocalDate(2026, 1, 5), creditCard = card),
    )

    @Test
    fun `balance of one account sums its account legs up to the month`() {
        assertEquals(110.0, useCase(target = YearMonth(2026, 3), transactions = transactions, accountId = 1))
    }

    @Test
    fun `balance across all accounts excludes card legs and future months`() {
        assertEquals(130.0, useCase(target = YearMonth(2026, 3), transactions = transactions))
    }
}
