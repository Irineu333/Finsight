package com.neoutils.finsight.ui.modal.budgetForm

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two profiles of design D13, as the budget form meets them.
 *
 * With one currency the form is **identical to the one it always was** — not a control
 * more — and the limit takes that currency because it is the only possible answer rather
 * than a silent default. With two, the choice exists and is pre-selected with the
 * currency of the **default account**, which is where the user actually spends; the base
 * currency is not the suggestion, because it only says where he reads totals.
 */
class LimitCurrencyChoiceTest {

    /**
     * What the registry offers. The choice narrows it to the currencies actually in
     * use — that narrowing is the rule under test, and it is the same whatever the
     * registry happens to hold.
     */
    private val OFFERED = listOf(
        CurrencyInfo("BRL", "R$", "Real brasileiro"),
        CurrencyInfo("USD", "US$", "Dólar americano"),
        CurrencyInfo("EUR", "€", "Euro"),
    )

    private val budget = Budget(
        id = 1,
        title = "Alimentação",
        categories = emptyList(),
        iconKey = "shopping",
        amount = 200.0,
        currency = "USD",
        limitType = LimitType.FIXED,
        createdAt = 0L,
    )

    @Test
    fun `one currency offers no control and the limit takes it`() {
        val choice = limitCurrencyChoice(
            existing = null,
            currencies = AccountCurrencies(inUse = listOf("USD"), ofDefaultAccount = "USD"),
            picked = null,
            offered = OFFERED,
        )

        assertFalse(choice.hasChoice, "no row at all — the form is the one it always was")
        assertFalse(choice.canChange, "there is nothing to choose")
        assertEquals("USD", choice.currency)
    }

    /**
     * The profile that motivates the whole rule: every account in a currency other than
     * the base. The user is single-currency and pays nothing for multi-currency — no
     * control, and a limit in the currency he actually spends in.
     */
    @Test
    fun `a single currency different from the base is still no choice at all`() {
        val choice = limitCurrencyChoice(
            existing = null,
            currencies = AccountCurrencies(inUse = listOf("USD"), ofDefaultAccount = "USD"),
            picked = null,
            offered = OFFERED,
        )

        assertFalse(choice.hasChoice)
        assertFalse(choice.canChange)
        assertEquals("USD", choice.currency)
    }

    @Test
    fun `two currencies offer the choice pre-selected with the default account's`() {
        val choice = limitCurrencyChoice(
            existing = null,
            currencies = AccountCurrencies(inUse = listOf("BRL", "USD"), ofDefaultAccount = "USD"),
            picked = null,
            offered = OFFERED,
        )

        assertTrue(choice.hasChoice)
        assertTrue(choice.canChange)
        assertEquals("USD", choice.currency, "where the user spends, not where he reads totals")
        assertEquals(listOf("BRL", "USD"), choice.selectable.map { it.code }.sorted())
    }

    @Test
    fun `what the user picks wins over the suggestion`() {
        val choice = limitCurrencyChoice(
            existing = null,
            currencies = AccountCurrencies(inUse = listOf("BRL", "USD"), ofDefaultAccount = "USD"),
            picked = "BRL",
            offered = OFFERED,
        )

        assertEquals("BRL", choice.currency)
    }

    @Test
    fun `editing shows the stored denomination locked whatever else exists`() {
        val choice = limitCurrencyChoice(
            existing = budget,
            currencies = AccountCurrencies(inUse = listOf("BRL", "USD"), ofDefaultAccount = "BRL"),
            picked = "BRL",
            offered = OFFERED,
        )

        assertEquals("USD", choice.currency, "a stored limit is never re-denominated")
        assertTrue(choice.hasChoice, "shown, so the saved denomination is readable")
        assertFalse(choice.canChange, "and locked, because changing it means another budget")
    }

    /**
     * The half of the previous case that is easy to lose: locking the row is not the same
     * as hiding it. A single-currency user still sees nothing when editing — there is no
     * denomination to disambiguate — so the two rules stay independent.
     */
    @Test
    fun `editing with one currency still shows nothing`() {
        val choice = limitCurrencyChoice(
            existing = budget,
            currencies = AccountCurrencies(inUse = listOf("USD"), ofDefaultAccount = "USD"),
            picked = null,
            offered = OFFERED,
        )

        assertFalse(choice.hasChoice)
        assertFalse(choice.canChange)
    }
}
