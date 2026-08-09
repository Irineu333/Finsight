@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.BuildTransactionError
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Every reason a form is not yet a transaction, and the date it means when it is.
 *
 * This is the whole point of the rule having moved off the form: it took a today as a
 * parameter defaulting to `Clock.System`, so testing it against another day meant either
 * passing one — which a caller could forget, and two of them did — or driving a screen. The
 * clock is a constructor argument now, so a day is just an argument to the constructor.
 *
 * No ViewModel, no dispatcher, no repository doubles: the rule was always pure, and now it
 * is reachable that way.
 */
class ValidateTransactionFormUseCaseTest {

    private val today = LocalDate(2026, 3, 10)

    private val validate = ValidateTransactionFormUseCaseImpl(clock = FixedClock(today))

    private val account = Account(id = 1, name = "Bank", type = AccountType.ASSET, currency = "BRL")

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1_000.0,
        closingDay = 1,
        dueDay = 10,
    )

    private val category = Category(
        id = 1,
        name = "Food",
        icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE,
        createdAt = 0,
    )

    @Test
    fun `a complete account form is valid and reports the date it means`() {
        assertEquals(LocalDate(2026, 3, 9), validate(form()).getOrNull())
    }

    @Test
    fun `a complete card form is valid`() {
        assertEquals(LocalDate(2026, 3, 9), validate(cardForm()).getOrNull())
    }

    @Test
    fun `today itself is not the future`() {
        assertEquals(today, validate(form(date = "10/03/2026")).getOrNull())
    }

    @Test
    fun `tomorrow is`() {
        assertEquals(
            BuildTransactionError.DateFuture,
            validate(form(date = "11/03/2026")).leftOrNull(),
        )
    }

    /**
     * The same date, judged by two clocks. This is the bug the parameter allowed: a screen
     * reading one today and the rule another, with nothing on screen to say which won.
     */
    @Test
    fun `a date is future or present according to the clock the use case was given`() {
        val date = "10/04/2026"

        assertEquals(
            BuildTransactionError.DateFuture,
            validate(form(date = date)).leftOrNull(),
        )

        val moved = ValidateTransactionFormUseCaseImpl(clock = FixedClock(LocalDate(2026, 4, 10)))

        assertEquals(LocalDate(2026, 4, 10), moved(form(date = date)).getOrNull())
    }

    @Test
    fun `an archived leg is refused before anything else`() {
        assertEquals(
            BuildTransactionError.ClosedSelection,
            validate(form(account = account.copy(isArchived = true))).leftOrNull(),
        )
    }

    @Test
    fun `an amount is required and cannot be zero`() {
        assertEquals(BuildTransactionError.AmountRequired, validate(form(amount = "")).leftOrNull())
        assertEquals(BuildTransactionError.AmountZero, validate(form(amount = "0,00")).leftOrNull())
    }

    @Test
    fun `a date is required and has to be one`() {
        assertEquals(BuildTransactionError.DateRequired, validate(form(date = "")).leftOrNull())
        assertEquals(BuildTransactionError.DateInvalid, validate(form(date = "32/13/2026")).leftOrNull())
    }

    @Test
    fun `a title or a category is required and either alone will do`() {
        assertEquals(
            BuildTransactionError.TitleOrCategoryRequired,
            validate(form(title = "", category = null)).leftOrNull(),
        )
        assertEquals(LocalDate(2026, 3, 9), validate(form(title = "", category = category)).getOrNull())
        assertEquals(LocalDate(2026, 3, 9), validate(form(title = "Lunch", category = null)).getOrNull())
    }

    @Test
    fun `an account form needs its account`() {
        assertEquals(
            BuildTransactionError.AccountRequired,
            validate(form(account = null)).leftOrNull(),
        )
    }

    /**
     * Built directly, not through `TransactionForm.from`: that factory already normalises an
     * income onto an account, so a screen cannot produce this form. The rule stays because the
     * type is a constructor parameter and nothing but this stops the pair being written.
     */
    @Test
    fun `only an expense goes on a card`() {
        val income = TransactionForm(
            type = TransactionType.INCOME,
            amount = "50,00",
            title = "Refund",
            date = "09/03/2026",
            category = null,
            target = TransactionTarget.CREDIT_CARD,
            creditCard = card,
            invoiceDueMonth = YearMonth(2026, 4),
            account = null,
        )

        assertEquals(BuildTransactionError.CreditCardExpenseOnly, validate(income).leftOrNull())
    }

    @Test
    fun `an income asked to go on a card is normalised onto an account before the rule sees it`() {
        assertEquals(
            BuildTransactionError.AccountRequired,
            validate(cardForm(type = TransactionType.INCOME)).leftOrNull(),
        )
    }

    @Test
    fun `a card form needs the card and the invoice it lands on`() {
        assertEquals(
            BuildTransactionError.CreditCardRequired,
            validate(cardForm(creditCard = null)).leftOrNull(),
        )
        assertEquals(
            BuildTransactionError.InvoiceRequired,
            validate(cardForm(invoiceDueMonth = null)).leftOrNull(),
        )
    }

    private fun form(
        type: TransactionType = TransactionType.EXPENSE,
        amount: String = "50,00",
        title: String? = "Groceries",
        date: String = "09/03/2026",
        category: Category? = this.category,
        account: Account? = this.account,
    ) = TransactionForm.from(
        type = type,
        amount = amount,
        title = title,
        date = date,
        category = category,
        target = TransactionTarget.ACCOUNT,
        creditCard = null,
        invoiceDueMonth = null,
        account = account,
    )

    private fun cardForm(
        type: TransactionType = TransactionType.EXPENSE,
        creditCard: CreditCard? = card,
        invoiceDueMonth: YearMonth? = YearMonth(2026, 4),
    ) = TransactionForm.from(
        type = type,
        amount = "50,00",
        title = "Groceries",
        date = "09/03/2026",
        category = category,
        target = TransactionTarget.CREDIT_CARD,
        creditCard = creditCard,
        invoiceDueMonth = invoiceDueMonth,
        account = null,
    )
}

private class FixedClock(private val today: LocalDate) : Clock {
    override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault())
}
