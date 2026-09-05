package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnarchiveCreditCardUseCaseTest {

    private class RecordingCreditCardRepository(
        private vararg val cards: CreditCard,
    ) : ICreditCardRepository {
        val unarchived = mutableListOf<Long>()
        override suspend fun unarchive(accountId: Long) { unarchived += accountId }
        override suspend fun currencyForNewCard(): String = throw NotImplementedError()
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
        override suspend fun getAllCreditCards(): List<CreditCard> = cards.toList()
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = cards.toList()
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? =
            cards.firstOrNull { it.id == creditCardId }
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
        override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    }

    private fun card(accountId: Long) = CreditCard(
        id = 1L,
        name = "Card",
        limit = 1000.0,
        closingDay = 10,
        dueDay = 20,
        accountId = accountId,
    )

    @Test
    fun `unarchive reopens the card's account and returns Right`() = runTest {
        val card = card(accountId = 42L)
        val repository = RecordingCreditCardRepository(card)

        val result = UnarchiveCreditCardUseCaseImpl(repository)(card)

        assertTrue(result.isRight())
        assertEquals(listOf(42L), repository.unarchived)
    }

    /**
     * The account to reopen is the one the card names **now**, so the card is read at
     * execution: an identity matching nothing has no account to name, and reporting the
     * card as back would be reporting an `UPDATE` that touched no row.
     */
    @Test
    fun `unarchiving a card that does not exist is refused and nothing is reopened`() = runTest {
        val repository = RecordingCreditCardRepository(card(accountId = 42L))

        val result = UnarchiveCreditCardUseCaseImpl(repository)(404L)

        assertEquals(
            CreditCardError.NOT_FOUND,
            (result.leftOrNull() as CreditCardException).error,
        )
        assertTrue(repository.unarchived.isEmpty(), "nothing may be reopened")
    }
}
