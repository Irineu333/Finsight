package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.Denomination
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.model.toTransactionUi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ties the sign [AdjustInvoiceUseCase] *records* to the sign the screen *shows*, end to
 * end. Each half is pinned elsewhere; only together do they stop the writer's convention
 * and the display's from drifting apart, which is exactly how `+R$ 100,00` came to be
 * shown for a debt that had grown.
 */
class InvoiceAdjustmentSignTest {

    private val date = LocalDate(2026, 1, 10)

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    private val invoice = Invoice(
        id = 1,
        creditCard = card,
        dimensionId = 1,
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 2),
        dueMonth = YearMonth(2026, 3),
        status = Invoice.Status.OPEN,
    )

    private val cardAccount = Account(id = card.accountId, name = card.name, type = AccountType.LIABILITY)

    /**
     * Adjusts the invoice to [target], optionally over an existing purchase of
     * [owedBefore]. The purchase is written straight into the store because it carries no
     * `EQUITY` leg — which is precisely what keeps the use case from mistaking it for the
     * adjustment it is looking for.
     */
    private suspend fun adjustmentOf(target: Double, owedBefore: Double = 0.0): Transaction {
        val ledger = InvoiceLedgerStore(card)

        if (owedBefore != 0.0) {
            ledger.dateByTransaction[PURCHASE_ID] = date
            ledger.entriesByTransaction[PURCHASE_ID] = listOf(
                Entry(
                    transactionId = PURCHASE_ID,
                    account = cardAccount,
                    amount = -(owedBefore * 100).toLong(),
                    dimensionId = invoice.dimensionId,
                ),
            )
        }

        AdjustInvoiceUseCase(
            transactionRepository = FakeTransactionRepository(ledger),
            calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(ledger)),
        )(invoice = invoice, target = target, adjustmentDate = date).getOrNull()

        val (id, entries) = ledger.entriesByTransaction
            .entries
            .single { (_, entries) -> entries.any { it.account.type == AccountType.EQUITY } }
            .toPair()

        return Transaction(id = id, title = null, date = date, entries = entries)
    }

    @Test
    fun raisingTheOwedAmountIsShownNegative() = runTest {
        val ui = adjustmentOf(target = 100.0).toTransactionUi(accountId = card.accountId)

        assertEquals(DisplayAmount.explicitSign(-100.0, Denomination.exact("BRL")), ui?.amount)
    }

    @Test
    fun loweringTheOwedAmountIsShownPositive() = runTest {
        val ui = adjustmentOf(target = 40.0, owedBefore = 100.0)
            .toTransactionUi(accountId = card.accountId)

        assertEquals(DisplayAmount.explicitSign(60.0, Denomination.exact("BRL")), ui?.amount)
    }

    private companion object {
        const val PURCHASE_ID = 100L
    }
}
