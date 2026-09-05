package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.TransactionError
import com.neoutils.finsight.domain.exception.TransactionException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.ui.modal.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Editing a transaction is a **total rewrite**, and this is where that fact is refused when it
 * cannot be honoured.
 *
 * `ITransactionRepository.updateTransaction` deletes every entry the transaction had and rebuilds it
 * from one leg plus its contra. That describes an expense or an income and nothing else — so a
 * transfer, a card payment, an adjustment and one share of an installment are refused here rather
 * than half-written, and the refusal names which of the four it was.
 *
 * Until this use case existed the write lived inside `EditTransactionViewModel`, which meant the
 * rule was the *screen's* and a second surface would have had to restate it.
 */
class UpdateTransactionUseCaseTest {

    private val checking = Account(id = 1, name = "Checking", type = AccountType.ASSET, currency = "BRL")
    private val savings = Account(id = 2, name = "Savings", type = AccountType.ASSET, currency = "BRL")
    private val card = Account(id = 3, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val groceries = Account(id = 10, name = "Groceries", type = AccountType.EXPENSE, currency = "BRL")
    private val reconciliation = Account(id = 11, name = "Reconciliation", type = AccountType.EQUITY, currency = "BRL")

    private val expense = Transaction(
        id = 1,
        title = "Lunch",
        date = LocalDate(2026, 3, 10),
        entries = listOf(
            Entry(account = checking, amount = -4500),
            Entry(account = groceries, amount = 4500),
        ),
    )

    private val transfer = Transaction(
        id = 2,
        title = "Move",
        date = LocalDate(2026, 3, 10),
        entries = listOf(
            Entry(account = checking, amount = -10_000),
            Entry(account = savings, amount = 10_000),
        ),
    )

    private val payment = Transaction(
        id = 3,
        title = "Card payment",
        date = LocalDate(2026, 3, 10),
        entries = listOf(
            Entry(account = checking, amount = -30_000),
            Entry(account = card, amount = 30_000),
        ),
    )

    private val adjustment = Transaction(
        id = 4,
        title = "Fix",
        date = LocalDate(2026, 3, 10),
        entries = listOf(
            Entry(account = checking, amount = 2_000),
            Entry(account = reconciliation, amount = -2_000),
        ),
    )

    private val share = expense.copy(id = 5, installmentId = 7, installmentNumber = 3)

    private val intent = TransactionIntent(
        title = "Dinner",
        date = LocalDate(2026, 3, 12),
        legs = listOf(TransactionLeg(TransactionType.EXPENSE, amount = 60.0, accountId = checking.id)),
        contra = ContraLeg(nature = AccountType.EXPENSE),
    )

    private val form = TransactionForm(
        type = TransactionType.EXPENSE,
        amount = "6000",
        title = "Dinner",
        date = "12/03/2026",
        category = null,
        target = com.neoutils.finsight.domain.model.TransactionTarget.ACCOUNT,
        creditCard = null,
        invoiceDueMonth = null,
        account = null,
    )

    private fun useCase(repository: FakeTransactionRepository) = UpdateTransactionUseCaseImpl(
        transactionRepository = repository,
        buildTransaction = Built(intent),
    )

    @Test
    fun `an expense is rewritten from the built intent, contra included`() = runTest {
        val repository = FakeTransactionRepository(stored = listOf(expense))

        val result = useCase(repository)(expense.id, form)

        assertTrue(result.isRight(), "the edit was refused: ${result.leftOrNull()}")
        assertEquals(1, repository.rewritten.size)
        val rewrite = repository.rewritten.single()
        assertEquals(expense.id, rewrite.id)
        assertEquals(intent.title, rewrite.title)
        assertEquals(intent.date, rewrite.date)
        assertEquals(intent.legs, rewrite.legs)
        assertEquals(
            intent.contra,
            rewrite.contra,
            "without the contra the rewrite is unbalanced and rolls the edit back",
        )
    }

    @Test
    fun `it answers the transaction as the ledger holds it after the rewrite`() = runTest {
        val repository = FakeTransactionRepository(stored = listOf(expense))

        val updated = useCase(repository)(expense.id, form).getOrNull()

        assertEquals(intent.title, updated?.title, "the answer echoed the request instead of the row")
        assertEquals(intent.date, updated?.date)
    }

    @Test
    fun `a transfer is refused for having more than one monetary leg`() = runTest {
        val repository = FakeTransactionRepository(stored = listOf(transfer))

        val error = assertIs<TransactionException>(useCase(repository)(transfer.id, form).leftOrNull())

        assertEquals(TransactionError.MULTIPLE_MONETARY_LEGS, error.error)
        assertTrue(repository.rewritten.isEmpty(), "the second leg would have been dropped")
    }

    @Test
    fun `a card payment is refused for the same reason`() = runTest {
        val repository = FakeTransactionRepository(stored = listOf(payment))

        val error = assertIs<TransactionException>(useCase(repository)(payment.id, form).leftOrNull())

        assertEquals(TransactionError.MULTIPLE_MONETARY_LEGS, error.error)
        assertTrue(repository.rewritten.isEmpty())
    }

    @Test
    fun `an adjustment is refused, because the rewrite would make it ordinary spending`() = runTest {
        val repository = FakeTransactionRepository(stored = listOf(adjustment))

        val error = assertIs<TransactionException>(useCase(repository)(adjustment.id, form).leftOrNull())

        assertEquals(TransactionError.IS_ADJUSTMENT, error.error)
        assertTrue(repository.rewritten.isEmpty())
    }

    @Test
    fun `one share of an installment is refused`() = runTest {
        val repository = FakeTransactionRepository(stored = listOf(share))

        val error = assertIs<TransactionException>(useCase(repository)(share.id, form).leftOrNull())

        assertEquals(TransactionError.INSTALLMENT_SHARE, error.error)
        assertTrue(repository.rewritten.isEmpty())
    }

    @Test
    fun `an identity that matches nothing is refused and nothing is written`() = runTest {
        val repository = FakeTransactionRepository(stored = listOf(expense))

        val error = assertIs<TransactionException>(useCase(repository)(404L, form).leftOrNull())

        assertEquals(TransactionError.NOT_FOUND, error.error)
        assertTrue(repository.rewritten.isEmpty())
    }

    @Test
    fun `editing by id and by transaction are the same operation`() = runTest {
        val byId = FakeTransactionRepository(stored = listOf(expense))
        val byTransaction = FakeTransactionRepository(stored = listOf(expense))

        val fromId = useCase(byId)(expense.id, form)
        val fromTransaction = useCase(byTransaction)(expense, form)

        assertEquals(fromId.isRight(), fromTransaction.isRight())
        assertEquals(byId.rewritten, byTransaction.rewritten)
    }
}

/** The build step, already decided: this test is about what happens around it. */
private class Built(private val intent: TransactionIntent) : BuildTransactionUseCase {
    override suspend fun invoke(form: TransactionForm): Either<Throwable, TransactionIntent> =
        intent.right()
}
