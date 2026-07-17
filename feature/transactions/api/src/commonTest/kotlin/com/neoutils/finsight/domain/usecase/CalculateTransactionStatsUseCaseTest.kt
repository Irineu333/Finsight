package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes [CalculateTransactionStatsUseCase]: income/expense/adjustment as Σ
 * amount of the month's account-target legs of each type. Unlike `AccountUi`, expense
 * here includes invoice payments and adjustment uses the raw amount. Task 4.11 flips
 * this to the ledger; the numbers must survive.
 */
class CalculateTransactionStatsUseCaseTest {

    private val useCase = CalculateTransactionStatsUseCase()
    private val account = Account(id = 1, name = "A", type = AccountType.ASSET)
    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)
    private val invoice = Invoice(
        id = 1, creditCard = card,
        openingMonth = YearMonth(2026, 2), closingMonth = YearMonth(2026, 3), dueMonth = YearMonth(2026, 4),
        status = Invoice.Status.OPEN,
    )

    private fun accountLeg(type: Transaction.Type, amount: Double, month: Int, invoice: Invoice? = null) = Transaction(
        type = type, amount = amount, title = null, date = LocalDate(2026, month, 10), account = account, invoice = invoice,
    )

    @Test
    fun `monthly stats sum account legs by type`() {
        val transactions = listOf(
            accountLeg(Transaction.Type.INCOME, 100.0, month = 3),
            accountLeg(Transaction.Type.EXPENSE, 30.0, month = 3),
            accountLeg(Transaction.Type.ADJUSTMENT, 40.0, month = 3),
            accountLeg(Transaction.Type.EXPENSE, 80.0, month = 3, invoice = invoice), // payment counted in expense
            accountLeg(Transaction.Type.INCOME, 999.0, month = 2),                    // other month → excluded
            Transaction(type = Transaction.Type.EXPENSE, amount = 55.0, title = null, date = LocalDate(2026, 3, 5), creditCard = card), // card leg → excluded
        )

        val stats = useCase(transactions = transactions, forYearMonth = YearMonth(2026, 3))

        assertEquals(100.0, stats.income)
        assertEquals(110.0, stats.expense)
        assertEquals(40.0, stats.adjustment)
    }
}
