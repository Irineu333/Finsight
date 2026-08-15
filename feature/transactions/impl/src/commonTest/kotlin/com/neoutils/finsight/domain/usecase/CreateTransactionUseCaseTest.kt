package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The composition that had no owner: form → intent → written transaction.
 *
 * What is asserted here is that it composes and *only* composes. It builds nothing of
 * its own — the intent that reaches the ledger is byte for byte the one the builder
 * produced —, it decides no rule, and it lets neither kind of failure escape: a build
 * that refuses stops before the write, and a write that **throws** comes back as a
 * `Left` rather than as a crash. The second is the reason the composition is worth
 * naming at all: every caller that wrote this by hand had to remember `catch {}`,
 * because the ledger's boundary raises exceptions and `either {}` intercepts a
 * `Raise`, not a throw.
 */
class CreateTransactionUseCaseTest {

    private val date = LocalDate(2026, 3, 12)

    private val intent = TransactionIntent(
        title = "Lunch",
        date = date,
        legs = listOf(
            TransactionLeg(type = TransactionType.EXPENSE, amount = 45.0, accountId = 1L),
        ),
        contra = ContraLeg(AccountType.EXPENSE, dimensionId = 7L),
    )

    /**
     * The form is never read here: the builder is a double. It only has to be the
     * shape the signature asks for.
     */
    private val form = TransactionForm.from(
        type = TransactionType.EXPENSE,
        amount = "4500",
        title = "Lunch",
        date = "12/03/2026",
        category = null,
        target = TransactionTarget.ACCOUNT,
        creditCard = null,
        invoiceDueMonth = null,
        account = null,
    )

    @Test
    fun `the intent the builder produced is the intent that is written`() = runTest {
        val repository = RecordingWrites()
        val useCase = CreateTransactionUseCaseImpl(Builds(intent), repository)

        val result = useCase(form)

        assertEquals(intent, repository.written.single(), "nothing is rebuilt in between")
        assertEquals(1L, result.getOrNull()?.id)
    }

    @Test
    fun `a build that refuses never reaches the ledger`() = runTest {
        val repository = RecordingWrites()
        val refusal = IllegalStateException("closed invoice")
        val useCase = CreateTransactionUseCaseImpl(Refuses(refusal), repository)

        val result = useCase(form)

        assertEquals(refusal, result.leftOrNull())
        assertTrue(repository.written.isEmpty(), "the write must not be attempted")
    }

    /**
     * The one that is easy to get wrong: the write boundary *throws* its refusals — a
     * closed account, an unbalanced intent — and an `Either` pipeline does not catch a
     * throw on its own.
     */
    @Test
    fun `a write that throws comes back as a refusal, not as a crash`() = runTest {
        val failure = IllegalStateException("account is archived")
        val useCase = CreateTransactionUseCaseImpl(Builds(intent), Throws(failure))

        val result = useCase(form)

        assertIs<Either.Left<Throwable>>(result)
        assertEquals(failure, result.value)
    }

    private class Builds(private val intent: TransactionIntent) : BuildTransactionUseCase {
        override suspend fun invoke(form: TransactionForm) = Either.Right(intent)
    }

    private class Refuses(private val error: Throwable) : BuildTransactionUseCase {
        override suspend fun invoke(form: TransactionForm) = Either.Left(error)
    }

    private class RecordingWrites : NotUnderTestTransactions() {
        val written = mutableListOf<TransactionIntent>()
        override suspend fun createTransaction(intent: TransactionIntent): Transaction {
            written += intent
            return Transaction(id = written.size.toLong(), title = intent.title, date = intent.date)
        }
    }

    private class Throws(private val failure: Throwable) : NotUnderTestTransactions() {
        override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw failure
    }
}
