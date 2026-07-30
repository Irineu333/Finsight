package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.model.toTransactionUi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ties the sign [AdjustBalanceUseCase] *records* to the sign the screen *shows*, end to
 * end — the account-side mirror of `InvoiceAdjustmentSignTest`. Correcting a balance
 * downwards reduces the user's net worth, so it reads negative; the writer's convention
 * and the display's cannot drift apart without this failing.
 */
class BalanceAdjustmentSignTest {

    private val date = LocalDate(2026, 1, 10)
    private val account = Account(id = 1, name = "Checking", type = AccountType.ASSET, currency = "BRL")
    private val salary = Account(id = 2, name = "Salary", type = AccountType.INCOME, currency = "BRL")

    /**
     * Adjusts the account to [target] over an existing balance of [balanceBefore]. The
     * income that produced that balance goes straight into the store because it carries
     * no `EQUITY` leg — which is what keeps the use case from mistaking it for the
     * adjustment it is looking for.
     */
    private suspend fun adjustmentOf(target: Double, balanceBefore: Double): Transaction {
        val ledger = LedgerStore(account)
        val cents = (balanceBefore * 100).toLong()

        ledger.dateByTransaction[INCOME_ID] = date
        ledger.entriesByTransaction[INCOME_ID] = listOf(
            Entry(transactionId = INCOME_ID, account = account, amount = cents),
            Entry(transactionId = INCOME_ID, account = salary, amount = -cents),
        )

        AdjustBalanceUseCase(
            transactionRepository = FakeTransactionRepository(ledger),
            calculateBalanceUseCase = CalculateBalanceUseCase(FakeEntryRepository(ledger)),
        )(targetBalance = target, adjustmentDate = date, account = account).getOrNull()

        val (id, entries) = ledger.entriesByTransaction
            .entries
            .single { (_, entries) -> entries.any { it.account.type == AccountType.EQUITY } }
            .toPair()

        return Transaction(id = id, title = null, date = date, entries = entries)
    }

    @Test
    fun loweringTheBalanceIsShownNegative() = runTest {
        val ui = adjustmentOf(target = 150.0, balanceBefore = 200.0)
            .toTransactionUi(accountId = account.id)

        assertEquals(DisplayAmount.explicitSign(-50.0, account.currency, isApproximate = false), ui?.amount)
    }

    @Test
    fun raisingTheBalanceIsShownPositive() = runTest {
        val ui = adjustmentOf(target = 250.0, balanceBefore = 200.0)
            .toTransactionUi(accountId = account.id)

        assertEquals(DisplayAmount.explicitSign(50.0, account.currency, isApproximate = false), ui?.amount)
    }

    private companion object {
        const val INCOME_ID = 100L
    }
}
