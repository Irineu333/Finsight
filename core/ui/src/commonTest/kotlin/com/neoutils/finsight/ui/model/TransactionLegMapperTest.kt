package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.view_transaction_leg_verb_adjusted
import com.neoutils.finsight.resources.view_transaction_leg_verb_charged
import com.neoutils.finsight.resources.view_transaction_leg_verb_entered
import com.neoutils.finsight.resources.view_transaction_leg_verb_left
import com.neoutils.finsight.resources.view_transaction_leg_verb_settled
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The operation surface's mapping, which is a claim about the **ledger**: the verb of
 * a leg comes from its account type and its sign, never from the transaction's nature,
 * and the figure is a magnitude wherever the verb already gives the direction.
 */
class TransactionLegMapperTest {

    private val date = LocalDate(2026, 1, 1)

    private fun entry(
        type: AccountType,
        amount: Long,
        currency: String = "BRL",
        name: String = type.name,
        isArchived: Boolean = false,
    ) = Entry(
        account = Account(
            id = 1L,
            name = name,
            type = type,
            currency = currency,
            isArchived = isArchived,
        ),
        amount = amount,
    )

    private fun transaction(vararg entries: Entry) =
        Transaction(id = 1L, title = "Op", date = date, entries = entries.toList())

    private fun verb(res: org.jetbrains.compose.resources.StringResource) = UiText.Res(res)

    // --- The verb table (design D2) ---

    @Test
    fun assetNegativeSaysTheMoneyLeft() {
        val legs = transaction(
            entry(AccountType.ASSET, -10_000),
            entry(AccountType.EXPENSE, 10_000),
        ).toTransactionLegs()

        assertEquals(verb(Res.string.view_transaction_leg_verb_left), legs.single().verb)
    }

    @Test
    fun assetPositiveSaysTheMoneyEntered() {
        val legs = transaction(
            entry(AccountType.INCOME, -10_000),
            entry(AccountType.ASSET, 10_000),
        ).toTransactionLegs()

        assertEquals(verb(Res.string.view_transaction_leg_verb_entered), legs.single().verb)
    }

    @Test
    fun liabilityNegativeSaysItWasChargedToTheCard() {
        val legs = transaction(
            entry(AccountType.LIABILITY, -10_000),
            entry(AccountType.EXPENSE, 10_000),
        ).toTransactionLegs()

        assertEquals(verb(Res.string.view_transaction_leg_verb_charged), legs.single().verb)
    }

    @Test
    fun liabilityPositiveSaysTheInvoiceWasSettled() {
        val legs = transaction(
            entry(AccountType.ASSET, -10_000),
            entry(AccountType.LIABILITY, 10_000),
        ).toTransactionLegs()

        assertEquals(verb(Res.string.view_transaction_leg_verb_left), legs[0].verb)
        assertEquals(verb(Res.string.view_transaction_leg_verb_settled), legs[1].verb)
    }

    @Test
    fun anEquityLegMakesEveryLegSayAdjusted() {
        // The override is over the **transaction**, and it does not care which account
        // the money sits on — the same test `deriveTransactionType` already makes.
        val accountAdjustment = transaction(
            entry(AccountType.ASSET, -10_000),
            entry(AccountType.EQUITY, 10_000),
        ).toTransactionLegs()
        val invoiceAdjustment = transaction(
            entry(AccountType.LIABILITY, -10_000),
            entry(AccountType.EQUITY, 10_000),
        ).toTransactionLegs()

        assertEquals(verb(Res.string.view_transaction_leg_verb_adjusted), accountAdjustment.single().verb)
        assertEquals(verb(Res.string.view_transaction_leg_verb_adjusted), invoiceAdjustment.single().verb)
    }

    // --- One card per monetary leg (design D1) ---

    @Test
    fun oneCardForAnExpenseAnIncomeAndACardPurchase() {
        assertEquals(
            1,
            transaction(
                entry(AccountType.ASSET, -10_000),
                entry(AccountType.EXPENSE, 10_000),
            ).toTransactionLegs().size
        )
        assertEquals(
            1,
            transaction(
                entry(AccountType.INCOME, -10_000),
                entry(AccountType.ASSET, 10_000),
            ).toTransactionLegs().size
        )
        assertEquals(
            1,
            transaction(
                entry(AccountType.LIABILITY, -10_000),
                entry(AccountType.EXPENSE, 10_000),
            ).toTransactionLegs().size
        )
    }

    @Test
    fun twoCardsForATransferAndForAPayment() {
        assertEquals(
            2,
            transaction(
                entry(AccountType.ASSET, -10_000, name = "Out"),
                entry(AccountType.ASSET, 10_000, name = "In"),
            ).toTransactionLegs().size
        )
        assertEquals(
            2,
            transaction(
                entry(AccountType.ASSET, -10_000),
                entry(AccountType.LIABILITY, 10_000),
            ).toTransactionLegs().size
        )
    }

    @Test
    fun aSingleCurrencyTransferStatesTheSameFigureTwice() {
        // The criterion is the count of monetary legs, never of currencies: repeating
        // the figure is the assertion that nothing was lost on the way.
        val legs = transaction(
            entry(AccountType.ASSET, -10_000, name = "Out"),
            entry(AccountType.ASSET, 10_000, name = "In"),
        ).toTransactionLegs()

        assertEquals(2, legs.size)
        assertEquals(100.0, legs[0].amount.value)
        assertEquals(100.0, legs[1].amount.value)
    }

    @Test
    fun theCategoryLegProducesNoCard() {
        val legs = transaction(
            entry(AccountType.ASSET, -10_000, name = "Wallet"),
            entry(AccountType.EXPENSE, 10_000, name = "Market"),
        ).toTransactionLegs()

        assertEquals(listOf("Wallet"), legs.map { it.name })
    }

    // --- Order (design D6) ---

    @Test
    fun theFirstCardIsTheLegMoneyLeft() {
        val legs = transaction(
            entry(AccountType.ASSET, 10_000, name = "In"),
            entry(AccountType.ASSET, -10_000, name = "Out"),
        ).toTransactionLegs()

        assertEquals(listOf("Out", "In"), legs.map { it.name })
    }

    // --- Currency marking ---

    @Test
    fun theCurrencyIsStatedOnlyWhenTheLegsDisagreeOnIt() {
        val crossCurrency = transaction(
            entry(AccountType.ASSET, -55_000, currency = "BRL"),
            entry(AccountType.LIABILITY, 10_000, currency = "USD"),
            entry(AccountType.CONVERSION, 55_000, currency = "BRL"),
            entry(AccountType.CONVERSION, -10_000, currency = "USD"),
        ).toTransactionLegs()
        assertEquals(listOf("BRL", "USD"), crossCurrency.map { it.currencyCode })

        val singleCurrency = transaction(
            entry(AccountType.ASSET, -5_000, currency = "USD"),
            entry(AccountType.EXPENSE, 5_000, currency = "USD"),
        ).toTransactionLegs()
        assertNull(singleCurrency.single().currencyCode)
    }

    @Test
    fun aCrossCurrencyOperationStatesBothFiguresUnconverted() {
        val legs = transaction(
            entry(AccountType.ASSET, -55_000, currency = "BRL"),
            entry(AccountType.ASSET, 10_000, currency = "USD"),
            entry(AccountType.CONVERSION, 55_000, currency = "BRL"),
            entry(AccountType.CONVERSION, -10_000, currency = "USD"),
        ).toTransactionLegs()

        assertEquals(550.0, legs[0].amount.value)
        assertEquals("BRL", legs[0].amount.currency)
        assertEquals(100.0, legs[1].amount.value)
        assertEquals("USD", legs[1].amount.currency)
    }

    // --- Sign policy (design D3) ---

    @Test
    fun everyCardOfALabelledOperationReadsAsAMagnitude() {
        val payment = transaction(
            entry(AccountType.ASSET, -10_000),
            entry(AccountType.LIABILITY, 10_000),
        ).toTransactionLegs()

        assertEquals(listOf(SignPolicy.MAGNITUDE, SignPolicy.MAGNITUDE), payment.map { it.amount.policy })
        assertEquals(listOf(100.0, 100.0), payment.map { it.amount.value })
    }

    @Test
    fun anAdjustmentSpellsItsSignOutAndKeepsTheLedgersOwn() {
        // The debt that grows reads negative, and the type of the account does not
        // invert it — that is a rule about balances, not about a leg.
        val invoiceAdjustment = transaction(
            entry(AccountType.LIABILITY, -10_000),
            entry(AccountType.EQUITY, 10_000),
        ).toTransactionLegs().single()

        assertEquals(SignPolicy.EXPLICIT_SIGN, invoiceAdjustment.amount.policy)
        assertEquals(-100.0, invoiceAdjustment.amount.value)

        val accountAdjustment = transaction(
            entry(AccountType.EQUITY, -10_000),
            entry(AccountType.ASSET, 10_000),
        ).toTransactionLegs().single()

        assertEquals(SignPolicy.EXPLICIT_SIGN, accountAdjustment.amount.policy)
        assertEquals(100.0, accountAdjustment.amount.value)
    }

    // --- The shortcut ---

    @Test
    fun anArchivedFacadeOffersNoShortcut() {
        val legs = transaction(
            entry(AccountType.ASSET, -10_000, name = "Open"),
            entry(AccountType.ASSET, 10_000, name = "Closed", isArchived = true),
        ).toTransactionLegs(onOpen = {})

        assertNotNull(legs[0].onClick)
        assertNull(legs[1].onClick)
    }
}
