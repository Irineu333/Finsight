package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
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
 * The rewrite, as one owner instead of a composition every caller repeated.
 *
 * Two of these assertions are about the shape of the rewrite rather than about this
 * class: it hands the boundary **one** monetary leg plus the contra the intent
 * carries. The single leg is the repository's documented restriction — a rewrite
 * deletes every old entry and rebuilds from what it is given, so a transaction with
 * two monetary legs would silently lose one. And the contra is not optional: omitting
 * it would turn a one-sided intent into an unbalanced write, refused at the boundary
 * with the edit rolled back and nothing to show for it.
 */
class UpdateTransactionUseCaseTest {

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
    fun `the rewrite carries the built title, date, single leg and contra`() = runTest {
        val repository = RecordingRewrites()
        val useCase = UpdateTransactionUseCaseImpl(Builds(intent), repository)

        useCase(transactionId = 42L, form = form)

        val rewrite = repository.rewrites.single()
        assertEquals(42L, rewrite.id)
        assertEquals("Lunch", rewrite.title)
        assertEquals(date, rewrite.date)
        assertEquals(intent.legs.single(), rewrite.leg)
        assertEquals(
            intent.contra,
            rewrite.contra,
            "the contra travels: without it the rewrite is unbalanced and refused",
        )
    }

    @Test
    fun `a build that refuses never reaches the ledger`() = runTest {
        val repository = RecordingRewrites()
        val refusal = IllegalStateException("closed invoice")
        val useCase = UpdateTransactionUseCaseImpl(Refuses(refusal), repository)

        val result = useCase(transactionId = 42L, form = form)

        assertEquals(refusal, result.leftOrNull())
        assertTrue(repository.rewrites.isEmpty())
    }

    @Test
    fun `a rewrite that throws comes back as a refusal, not as a crash`() = runTest {
        val failure = IllegalStateException("invoice is paid")
        val useCase = UpdateTransactionUseCaseImpl(Builds(intent), Throws(failure))

        val result = useCase(transactionId = 42L, form = form)

        assertIs<Either.Left<Throwable>>(result)
        assertEquals(failure, result.value)
    }

    private data class Rewrite(
        val id: Long,
        val title: String?,
        val date: LocalDate,
        val leg: TransactionLeg,
        val contra: ContraLeg?,
    )

    private class Builds(private val intent: TransactionIntent) : BuildTransactionUseCase {
        override suspend fun invoke(form: TransactionForm) = Either.Right(intent)
    }

    private class Refuses(private val error: Throwable) : BuildTransactionUseCase {
        override suspend fun invoke(form: TransactionForm) = Either.Left(error)
    }

    private class RecordingRewrites : NotUnderTestTransactions() {
        val rewrites = mutableListOf<Rewrite>()
        override suspend fun updateTransaction(
            id: Long,
            title: String?,
            date: LocalDate,
            leg: TransactionLeg,
            contra: ContraLeg?,
        ) {
            rewrites += Rewrite(id, title, date, leg, contra)
        }
    }

    private class Throws(private val failure: Throwable) : NotUnderTestTransactions() {
        override suspend fun updateTransaction(
            id: Long,
            title: String?,
            date: LocalDate,
            leg: TransactionLeg,
            contra: ContraLeg?,
        ): Unit = throw failure
    }
}
