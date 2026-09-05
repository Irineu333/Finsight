package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.TransactionError
import com.neoutils.finsight.domain.exception.TransactionException
import com.neoutils.finsight.ui.modal.FakeTransactionRepository
import com.neoutils.finsight.ui.modal.transaction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The transaction removal is identified by **id**, and that form is the one that
 * carries the rule.
 *
 * The two properties are the pair that makes the second form safe to offer: an
 * identity matching nothing is refused with `NOT_FOUND` before anything is removed,
 * and the two forms of the use case produce the same result for the same identity —
 * because the one taking the transaction only extracts its id.
 *
 * Resolving rather than trusting the identity is what the first test is about:
 * `deleteTransactionById` removes by id, so an identity that matches nothing used to
 * be indistinguishable from a removal that worked.
 */
class TransactionUseCaseIdentityTest {

    private val stored = transaction(id = 1L)
    private val absent = transaction(id = 404L)

    @Test
    fun `deleting a transaction that does not exist is refused and nothing is removed`() = runTest {
        val repository = FakeTransactionRepository(stored = listOf(stored))

        val error = assertIs<TransactionException>(
            DeleteTransactionUseCaseImpl(repository)(absent.id).leftOrNull()
        )

        assertEquals(TransactionError.NOT_FOUND, error.error)
        assertTrue(repository.deleted.isEmpty(), "nothing may be removed")
    }

    @Test
    fun `deleting by id and by transaction are the same operation`() = runTest {
        val byId = FakeTransactionRepository(stored = listOf(stored))
        val byTransaction = FakeTransactionRepository(stored = listOf(stored))

        val fromId = DeleteTransactionUseCaseImpl(byId)(stored.id)
        val fromTransaction = DeleteTransactionUseCaseImpl(byTransaction)(stored)

        assertEquals(fromId.isRight(), fromTransaction.isRight())
        assertEquals(byId.deleted, byTransaction.deleted)
        assertEquals(listOf(stored.id), byId.deleted)
    }

    @Test
    fun `a transaction the caller holds but the store no longer has is refused`() = runTest {
        // The caller's copy is a reading that can already be out of date, and the
        // resolution happens when the removal runs — not when the screen loaded it.
        val repository = FakeTransactionRepository(stored = emptyList())

        val error = assertIs<TransactionException>(
            DeleteTransactionUseCaseImpl(repository)(stored).leftOrNull()
        )

        assertEquals(TransactionError.NOT_FOUND, error.error)
        assertTrue(repository.deleted.isEmpty())
    }
}
