package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Operation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes [CalculateTransactionStatsUseCase] over the ledger (task 4.11): income/
 * expense/adjustment as the ASSET legs of the month's operations, classified by
 * `deriveTransactionType`. Callers pass operations already stripped of transfers and
 * payments, so those never reach here — the payment-in-expense of the legacy isolated
 * form was never a production figure (both real callers pre-excluded payments). The
 * ViewModel-level guards (TransactionsViewModel/Dashboard tests) pin the production
 * numbers.
 */
class CalculateTransactionStatsUseCaseTest {

    private val useCase = CalculateTransactionStatsUseCase()
    private val account = Account(id = 1, name = "A", type = AccountType.ASSET)
    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME)
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE)
    private val equityAcc = Account(id = 102, name = "reconciliation", type = AccountType.EQUITY)

    private fun cents(amount: Double) = (amount * 100).toLong()
    private fun entry(acc: Account, amount: Double) = Entry(account = acc, amount = cents(amount))
    private fun op(date: LocalDate, entries: List<Entry>) =
        Operation(title = null, date = date, transactions = emptyList(), entries = entries)

    @Test
    fun `monthly stats classify the asset legs by direction`() {
        val operations = listOf(
            op(LocalDate(2026, 3, 10), listOf(entry(account, 100.0), entry(incomeAcc, -100.0))),
            op(LocalDate(2026, 3, 10), listOf(entry(account, -30.0), entry(expenseAcc, 30.0))),
            op(LocalDate(2026, 3, 10), listOf(entry(account, 40.0), entry(equityAcc, -40.0))),
            op(LocalDate(2026, 2, 10), listOf(entry(account, 999.0), entry(incomeAcc, -999.0))), // other month → excluded
        )

        val stats = useCase(operations = operations, forYearMonth = YearMonth(2026, 3))

        assertEquals(100.0, stats.income)
        assertEquals(30.0, stats.expense)
        assertEquals(40.0, stats.adjustment)
    }
}
