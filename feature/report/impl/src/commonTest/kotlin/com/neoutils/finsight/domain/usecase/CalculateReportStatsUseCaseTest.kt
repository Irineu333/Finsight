package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes [CalculateReportStatsUseCase] over the ledger (tasks 4.6/4.7): each
 * transaction carries its hydrated [Entry] legs, direction is derived from the sign of
 * the scope's leg plus the presence of an EQUITY counter-leg, and internal transfers
 * are detected from the ASSET legs — no `TransactionType`/`Target`/`Transaction.Kind`.
 * The figures are the same the legacy leg-based form produced. Transactions that the old
 * test bundled two unrelated legs into (an income and an adjustment on one card) are
 * modelled here as the separate ledger events they really are; the aggregate is equal.
 */
class CalculateReportStatsUseCaseTest {

    private val useCase = CalculateReportStatsUseCase()

    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME)
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE)
    private val equityAcc = Account(id = 102, name = "reconciliation", type = AccountType.EQUITY)
    private val paymentSource = Account(id = 103, name = "checking", type = AccountType.ASSET)

    private fun cents(amount: Double) = (amount * 100).roundToLong()

    private fun op(date: LocalDate, entries: List<Entry>) =
        Transaction(title = null, date = date, entries = entries)

    private fun entry(account: Account, amount: Double) = Entry(account = account, amount = cents(amount))

    private fun accountIncome(account: Account, amount: Double, date: LocalDate) =
        op(date, listOf(entry(account, amount), entry(incomeAcc, -amount)))

    private fun accountExpense(account: Account, amount: Double, date: LocalDate) =
        op(date, listOf(entry(account, -amount), entry(expenseAcc, amount)))

    /** [amount] is signed: negative lowers the balance, positive raises it. */
    private fun accountAdjustment(account: Account, amount: Double, date: LocalDate) =
        op(date, listOf(entry(account, amount), entry(equityAcc, -amount)))

    private fun transfer(source: Account, destination: Account, amount: Double, date: LocalDate) =
        op(date, listOf(entry(source, -amount), entry(destination, amount)))

    private fun cardExpense(cardLiability: Account, amount: Double, date: LocalDate) =
        op(date, listOf(entry(cardLiability, -amount), entry(expenseAcc, amount)))

    private fun cardPayment(cardLiability: Account, amount: Double, date: LocalDate) =
        op(date, listOf(entry(cardLiability, amount), entry(paymentSource, -amount)))

    private fun cardAdjustment(cardLiability: Account, amount: Double, date: LocalDate) =
        op(date, listOf(entry(cardLiability, amount), entry(equityAcc, -amount)))

    @Test
    fun accountPerspectiveIncludesAdjustmentsInPeriodAndOpeningBalance() {
        val account = Account(id = 1, name = "Carteira", type = AccountType.ASSET)
        val otherAccount = Account(id = 2, name = "Conta Secundaria", type = AccountType.ASSET)

        val transactions = listOf(
            accountIncome(account, 100.0, LocalDate(2026, 3, 1)),
            accountAdjustment(account, -30.0, LocalDate(2026, 3, 5)),
            accountExpense(account, 40.0, LocalDate(2026, 3, 10)),
            accountAdjustment(account, 25.0, LocalDate(2026, 3, 12)),
            accountIncome(otherAccount, 500.0, LocalDate(2026, 3, 12)),
        )

        val result = useCase(
            transactions = transactions,
            scope = ReportLedgerScope.Accounts(setOf(account.id)),
            startDate = LocalDate(2026, 3, 10),
            endDate = LocalDate(2026, 3, 31),
        )

        assertEquals(0.0, result.income)
        assertEquals(40.0, result.expense)
        assertEquals(-15.0, result.balance)
        assertEquals(70.0, result.openingBalance)
    }

    @Test
    fun creditCardPerspectiveIncludesAdjustmentsInPeriodAndOpeningBalance() {
        val cardLiability = Account(id = 200, name = "Visa", type = AccountType.LIABILITY)
        val otherCardLiability = Account(id = 201, name = "Master", type = AccountType.LIABILITY)

        val transactions = listOf(
            cardExpense(cardLiability, 100.0, LocalDate(2026, 2, 25)),
            cardPayment(cardLiability, 30.0, LocalDate(2026, 2, 28)),
            cardAdjustment(cardLiability, -5.0, LocalDate(2026, 2, 28)),
            cardExpense(cardLiability, 200.0, LocalDate(2026, 3, 15)),
            cardPayment(cardLiability, 80.0, LocalDate(2026, 3, 16)),
            cardAdjustment(cardLiability, 10.0, LocalDate(2026, 3, 16)),
            cardExpense(otherCardLiability, 999.0, LocalDate(2026, 3, 16)),
        )

        val result = useCase(
            transactions = transactions,
            scope = ReportLedgerScope.Card(liabilityAccountId = cardLiability.id),
            startDate = LocalDate(2026, 3, 1),
            endDate = LocalDate(2026, 3, 31),
        )

        assertEquals(80.0, result.income)
        assertEquals(200.0, result.expense)
        assertEquals(-110.0, result.balance)
        assertEquals(-75.0, result.openingBalance)
    }

    @Test
    fun accountPerspectiveIgnoresInternalTransfersBetweenSelectedAccounts() {
        val accountA = Account(id = 1, name = "Conta A", type = AccountType.ASSET)
        val accountB = Account(id = 2, name = "Conta B", type = AccountType.ASSET)
        val accountC = Account(id = 3, name = "Conta C", type = AccountType.ASSET)

        val transactions = listOf(
            transfer(accountA, accountB, 100.0, LocalDate(2026, 3, 10)),  // internal → excluded
            transfer(accountA, accountC, 30.0, LocalDate(2026, 3, 11)),   // A→outside → A leg counts
            accountIncome(accountA, 50.0, LocalDate(2026, 3, 12)),
            accountExpense(accountB, 20.0, LocalDate(2026, 3, 12)),
        )

        val result = useCase(
            transactions = transactions,
            scope = ReportLedgerScope.Accounts(setOf(accountA.id, accountB.id)),
            startDate = LocalDate(2026, 3, 1),
            endDate = LocalDate(2026, 3, 31),
        )

        assertEquals(50.0, result.income)
        assertEquals(50.0, result.expense)
        assertEquals(0.0, result.balance)
    }

    // Task 4.9 (CAP-4): an [Entry] carries no date — the transaction's date alone governs
    // which side of the period cut every one of its legs lands on. Two same-shape income
    // transactions differing only by date must split cleanly: the earlier into the opening
    // balance, the later into the period. A future caller that tried to cut by anything
    // other than the transaction date would break this.
    @Test
    fun `the transaction date governs the period cut`() {
        val account = Account(id = 1, name = "Carteira", type = AccountType.ASSET)
        val transactions = listOf(
            accountIncome(account, 100.0, LocalDate(2026, 2, 28)), // before the period → opening
            accountIncome(account, 40.0, LocalDate(2026, 3, 1)),   // inside the period → income
        )

        val result = useCase(
            transactions = transactions,
            scope = ReportLedgerScope.Accounts(setOf(account.id)),
            startDate = LocalDate(2026, 3, 1),
            endDate = LocalDate(2026, 3, 31),
        )

        assertEquals(100.0, result.openingBalance)
        assertEquals(40.0, result.income)
        assertEquals(40.0, result.balance)
    }
}
