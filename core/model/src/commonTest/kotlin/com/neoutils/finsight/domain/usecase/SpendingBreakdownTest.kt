package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules the one breakdown builder concentrates: the sign, the discarding of zeros,
 * the single scale the unclassified total takes part in, the position it is pinned to,
 * and the share that comes off that scale.
 *
 * What they are worth asserting for is the sentence the feature exists to make true —
 * the shares describe the whole period, not the part of it that happened to be
 * classified.
 */
class SpendingBreakdownTest {

    private val march = LocalDate(2026, 3, 31)

    private fun reducer(
        base: String = "BRL",
        currenciesInUse: List<String> = listOf("BRL"),
        vararg rates: Pair<String, Double>,
    ) = ConsolidateMoneyUseCase(
        baseCurrencyRepository = FakeBaseCurrency(base),
        exchangeRateRepository = FakeRates(rates.toMap()),
        getAccountCurrencies = FakeAccountCurrencies(currenciesInUse),
    )

    private fun category(id: Long, name: String) = SpendingSubject.Categorized(
        Category(
            id = id,
            name = name,
            icon = CategoryLazyIcon("food"),
            type = Category.Type.EXPENSE,
            createdAt = 0,
            dimensionId = id,
        )
    )

    /** Expenses post in debit, so the display sign that turns them positive is `1`. */
    private fun brl(value: Double) = MoneyByCurrency.of("BRL", value)

    private val food = category(1, "Alimentação")
    private val transport = category(2, "Transporte")

    @Test
    fun `the shares close the period`() = runTest {
        val breakdown = reducer().spendingBreakdown(
            totals = mapOf(
                food to brl(700.0),
                SpendingSubject.Uncategorized to brl(300.0),
            ),
            displaySign = 1,
            on = march,
        )

        assertEquals(listOf(food, SpendingSubject.Uncategorized), breakdown.map { it.subject })
        assertEquals(70.0, breakdown[0].percentage)
        assertEquals(30.0, breakdown[1].percentage)
        assertEquals(100.0, breakdown.sumOf { it.percentage!! })
    }

    @Test
    fun `a category's share shrinks while its amount does not move`() = runTest {
        val alone = reducer().spendingBreakdown(
            totals = mapOf(food to brl(700.0)),
            displaySign = 1,
            on = march,
        ).single()

        val beside = reducer().spendingBreakdown(
            totals = mapOf(food to brl(700.0), SpendingSubject.Uncategorized to brl(300.0)),
            displaySign = 1,
            on = march,
        ).first()

        assertEquals(100.0, alone.percentage)
        assertEquals(70.0, beside.percentage)
        assertEquals(alone.amount, beside.amount, "the money is the same money; only the whole changed")
    }

    @Test
    fun `bigger than every category and still last`() = runTest {
        val breakdown = reducer().spendingBreakdown(
            totals = mapOf(
                food to brl(100.0),
                SpendingSubject.Uncategorized to brl(900.0),
                transport to brl(50.0),
            ),
            displaySign = 1,
            on = march,
        )

        assertEquals(
            listOf(food, transport, SpendingSubject.Uncategorized),
            breakdown.map { it.subject },
            "categories rank among themselves by magnitude; the unclassified line does not compete",
        )
        // 900 of the period's 1050.
        assertEquals(900.0 / 1050 * 100, breakdown.last().percentage)
    }

    @Test
    fun `a fully classified period is exactly what it was`() = runTest {
        val breakdown = reducer().spendingBreakdown(
            totals = mapOf(food to brl(700.0), transport to brl(300.0)),
            displaySign = 1,
            on = march,
        )

        assertEquals(listOf(food, transport), breakdown.map { it.subject })
        assertEquals(listOf(70.0, 30.0), breakdown.map { it.percentage })
    }

    @Test
    fun `a zero unclassified total produces no line`() = runTest {
        val breakdown = reducer().spendingBreakdown(
            totals = mapOf(food to brl(700.0), SpendingSubject.Uncategorized to brl(0.0)),
            displaySign = 1,
            on = march,
        )

        assertEquals(listOf(food), breakdown.map { it.subject })
        assertEquals(100.0, breakdown.single().percentage)
    }

    @Test
    fun `a period with no movement gets no line at all`() = runTest {
        val breakdown = reducer().spendingBreakdown(
            totals = emptyMap(),
            displaySign = 1,
            on = march,
        )

        assertEquals(emptyList(), breakdown)
    }

    @Test
    fun `income is turned positive by its own display sign`() = runTest {
        // Income posts in credit, so the ledger's natural totals are negative.
        val breakdown = reducer().spendingBreakdown(
            totals = mapOf(
                category(3, "Salário") to brl(-3_000.0),
                SpendingSubject.Uncategorized to brl(-1_000.0),
            ),
            displaySign = -1,
            on = march,
        )

        assertTrue(breakdown.all { term -> term.amount.terms.all { it.value > 0 } })
        assertEquals(listOf(75.0, 25.0), breakdown.map { it.percentage })
    }

    /**
     * The whole is unknown, so nobody has a share — including the categories whose own
     * figures are perfectly exact. A denominator built from only the measurable ones
     * would hand them 100% of something that is not the total.
     */
    @Test
    fun `an unclassified total no rate reaches leaves nobody with a bar`() = runTest {
        val breakdown = reducer(base = "BRL", currenciesInUse = listOf("BRL", "JPY"))
            .spendingBreakdown(
                totals = mapOf(
                    food to brl(700.0),
                    SpendingSubject.Uncategorized to MoneyByCurrency.of("JPY", 5_000.0),
                ),
                displaySign = 1,
                on = march,
            )

        assertEquals(2, breakdown.size)
        breakdown.forEach { assertNull(it.percentage) }
    }
}
