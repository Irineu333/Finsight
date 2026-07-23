package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnarchiveCreditCardUseCaseTest {

    private class RecordingCreditCardRepository : ICreditCardRepository {
        val unarchived = mutableListOf<Long>()
        override suspend fun unarchive(accountId: Long) { unarchived += accountId }
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
        override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = throw NotImplementedError()
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
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
        val repository = RecordingCreditCardRepository()

        val result = UnarchiveCreditCardUseCase(repository)(card(accountId = 42L))

        assertTrue(result.isRight())
        assertEquals(listOf(42L), repository.unarchived)
    }
}
