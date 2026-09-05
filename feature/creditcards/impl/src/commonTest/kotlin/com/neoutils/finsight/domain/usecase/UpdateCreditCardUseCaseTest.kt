package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **A card the edit would leave invalid is refused, and nothing is written.**
 *
 * The edit is expressed as a transformation of the stored card, so what it produces is a
 * `CreditCard` — and a `CreditCard` cannot exist with a negative limit, which is the rule
 * `CreditCardForm.build` applies to a card being created. Zero is not refused by either:
 * it is a card whose limit was never set.
 *
 * These pin the refusal at the use case, which is where every caller meets it — the
 * screens through the form, an agent through `update_card`, and neither with a rule of
 * its own.
 */
class UpdateCreditCardUseCaseTest {

    private val stored = CreditCard(
        id = 1,
        name = "Nubank",
        limit = 1_000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    private fun useCase(cards: RecordingCards) = UpdateCreditCardUseCaseImpl(
        repository = cards,
        validateCreditCardName = ValidateCreditCardNameUseCase(cards),
    )

    @Test
    fun `a negative limit is refused and nothing is written`() = runTest {
        val cards = RecordingCards(stored)

        val result = useCase(cards)(stored.id) { it.copy(limit = -5_000.0) }

        val error = assertIs<CreditCardException>(result.leftOrNull())
        assertEquals(CreditCardError.NEGATIVE_LIMIT, error.error)
        assertTrue(cards.updates.isEmpty())
    }

    @Test
    fun `a limit of zero is a card with no limit set, and goes through`() = runTest {
        val cards = RecordingCards(stored)

        val result = useCase(cards)(stored.id) { it.copy(limit = 0.0) }

        assertTrue(result.isRight())
        assertEquals(0.0, cards.updates.single().limit)
    }

    @Test
    fun `a positive limit is stored`() = runTest {
        val cards = RecordingCards(stored)

        val result = useCase(cards)(stored.id) { it.copy(limit = 5_000.0) }

        assertTrue(result.isRight())
        assertEquals(5_000.0, cards.updates.single().limit)
    }

    @Test
    fun `an identity that matches nothing is refused before the edit is applied`() = runTest {
        val cards = RecordingCards()

        val result = useCase(cards)(stored.id) { it.copy(limit = 5_000.0) }

        val error = assertIs<CreditCardException>(result.leftOrNull())
        assertEquals(CreditCardError.NOT_FOUND, error.error)
        assertTrue(cards.updates.isEmpty())
    }
}

/** Resolves by id out of what was seeded, and records what the edit writes back. */
private class RecordingCards(vararg seed: CreditCard) : ICreditCardRepository {

    private val rows = seed.toList()

    val updates = mutableListOf<CreditCard>()

    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? =
        rows.firstOrNull { it.id == creditCardId }

    override suspend fun getAllCreditCards(): List<CreditCard> = rows
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = rows

    override suspend fun update(creditCard: CreditCard) { updates += creditCard }

    override fun observeAllCreditCards(): Flow<List<CreditCard>> = notUnderTest()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = notUnderTest()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = notUnderTest()
    override suspend fun currencyForNewCard(): String = notUnderTest()
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = notUnderTest()
    override suspend fun delete(creditCard: CreditCard) = notUnderTest()
    override suspend fun unarchive(accountId: Long) = notUnderTest()

    private fun notUnderTest(): Nothing = error("not part of the card edit under test")
}
