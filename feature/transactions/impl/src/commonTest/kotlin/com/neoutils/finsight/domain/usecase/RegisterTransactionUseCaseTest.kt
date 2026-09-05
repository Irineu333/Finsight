@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionRegistration
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.ui.modal.FakeTransactionRepository
import com.neoutils.finsight.ui.modal.RecordingRecurringRepository
import com.neoutils.finsight.ui.modal.transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The dispatch between an instalment plan, a recurring template and a plain
 * transaction, exercised where it lives.
 *
 * It used to be an `if` inside the sheet's `submit()`, reachable only by driving the
 * ViewModel; a second surface registering a transaction would have needed its own
 * copy. What each test pins is which of the three paths a form takes, and that the
 * answer names what was written.
 */
class RegisterTransactionUseCaseTest {

    private val today = LocalDate(2026, 3, 12)

    private val account = Account(
        id = 1,
        name = "Wallet",
        type = AccountType.ASSET,
        currency = "BRL",
        isDefault = true,
    )

    private val card = CreditCard(
        id = 1,
        accountId = 2,
        name = "Card",
        limit = 5000.0,
        closingDay = 20,
        dueDay = 27,
    )

    /** The dimension of the invoice the caller picked — it must survive to the template. */
    private val invoiceDimension = 77L

    @Test
    fun `a split form is registered as the N transactions of the plan`() = runTest {
        val transactions = FakeTransactionRepository()
        val recurring = RecordingRecurringRepository()
        val installments = WritesInstallments(count = 3)

        val registration = useCase(transactions, recurring, installments)(
            form = form(target = TransactionTarget.CREDIT_CARD, installments = 3),
            isRecurring = false,
        ).getOrNull()

        val plan = assertIs<TransactionRegistration.Installments>(registration)
        assertEquals(3, plan.transactions.size, "the answer carries the N that were written")
        assertEquals(3, installments.asked.single(), "the count comes off the form")
        assertTrue(transactions.created.isEmpty(), "the instalments are written by their own path")
        assertTrue(recurring.created.isEmpty())
    }

    @Test
    fun `a marked form is registered as the first cycle of a template`() = runTest {
        val transactions = FakeTransactionRepository()
        val recurring = RecordingRecurringRepository()

        val registration = useCase(transactions, recurring)(
            form = form(),
            isRecurring = true,
        ).getOrNull()

        val single = assertIs<TransactionRegistration.Single>(registration)
        assertEquals(1, single.transactions.size)

        val born = recurring.created.single()
        assertEquals(1, born.firstCycle.recurringCycle, "a template opens on cycle 1")
        assertEquals(
            invoiceDimension,
            born.firstCycle.legs.single().dimensionId,
            "the intent is completed, never rebuilt",
        )
        assertEquals(today.day, born.recurring.dayOfMonth)
        assertTrue(
            transactions.created.isEmpty(),
            "the transaction is written by the cycle, not beside it",
        )
    }

    @Test
    fun `a plain form is registered as one transaction`() = runTest {
        val transactions = FakeTransactionRepository()
        val recurring = RecordingRecurringRepository()

        val registration = useCase(transactions, recurring)(
            form = form(),
            isRecurring = false,
        ).getOrNull()

        val single = assertIs<TransactionRegistration.Single>(registration)
        assertEquals(1, single.transactions.size)
        assertEquals(
            invoiceDimension,
            transactions.created.single().legs.single().dimensionId,
            "what was built is what was written",
        )
        assertTrue(recurring.created.isEmpty(), "nothing repeats")
    }

    @Test
    fun `a split wins over the mark, and no template is born`() = runTest {
        // The sheet drops the mark when the purchase is split, so the two never arrive
        // together from it. A caller with no such screen gets the same precedence.
        val transactions = FakeTransactionRepository()
        val recurring = RecordingRecurringRepository()

        val registration = useCase(transactions, recurring, WritesInstallments(count = 2))(
            form = form(target = TransactionTarget.CREDIT_CARD, installments = 2),
            isRecurring = true,
        ).getOrNull()

        assertIs<TransactionRegistration.Installments>(registration)
        assertTrue(recurring.created.isEmpty())
        assertTrue(transactions.created.isEmpty())
    }

    private fun useCase(
        transactions: FakeTransactionRepository,
        recurring: RecordingRecurringRepository,
        installments: AddInstallmentUseCase = RefusesInstallments,
    ) = RegisterTransactionUseCaseImpl(
        transactionRepository = transactions,
        buildTransaction = BuildsWithInvoice(invoiceDimension),
        addInstallment = installments,
        startRecurringFromTransaction = StartRecurringFromTransactionUseCase(
            repository = recurring,
            clock = ClockOn(today),
        ),
    )

    private fun form(
        target: TransactionTarget = TransactionTarget.ACCOUNT,
        installments: Int = 1,
    ) = TransactionForm.from(
        type = TransactionType.EXPENSE,
        amount = "240000",
        title = "Rent",
        date = "12/03/2026",
        category = null,
        target = target,
        creditCard = card,
        invoiceDueMonth = YearMonth(2026, 4),
        account = account,
        installments = installments,
    )

    /** Stands in for the real build: what matters here is that its output travels intact. */
    private inner class BuildsWithInvoice(private val dimensionId: Long) : BuildTransactionUseCase {
        override suspend fun invoke(form: TransactionForm) = Either.Right(
            TransactionIntent(
                title = form.title,
                date = today,
                legs = listOf(
                    TransactionLeg(
                        type = form.type,
                        amount = 2400.0,
                        accountId = account.id,
                        dimensionId = dimensionId,
                    )
                ),
                contra = null,
            )
        )
    }

    /** Answers the N transactions the plan would write, and records the count it was asked for. */
    private class WritesInstallments(private val count: Int) : AddInstallmentUseCase {
        val asked = mutableListOf<Int>()

        override suspend fun invoke(
            form: TransactionForm,
            installments: Int,
        ): Either<Throwable, List<Transaction>> {
            asked += installments
            return Either.Right(List(count) { transaction(id = (it + 1).toLong()) })
        }
    }

    /** The path a form with a single instalment must never take. */
    private object RefusesInstallments : AddInstallmentUseCase {
        override suspend fun invoke(
            form: TransactionForm,
            installments: Int,
        ): Either<Throwable, List<Transaction>> = throw NotImplementedError()
    }

    private class ClockOn(private val today: LocalDate) : Clock {
        override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault())
    }
}
