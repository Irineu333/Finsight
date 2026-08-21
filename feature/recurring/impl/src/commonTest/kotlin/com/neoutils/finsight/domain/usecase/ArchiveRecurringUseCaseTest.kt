package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.recurring
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchiveRecurringUseCaseTest {

    @Test
    fun `archiving writes the flag`() = runTest {
        val target = recurring()
        val repository = FakeRecurringRepository(stored = listOf(target))

        assertTrue(ArchiveRecurringUseCaseImpl(repository)(target).isRight())

        assertEquals(listOf(true), repository.updated.map { it.isArchived })
    }

    @Test
    fun `unarchiving clears the flag and is refused by no invariant`() = runTest {
        val target = recurring(isArchived = true)
        val repository = FakeRecurringRepository(stored = listOf(target))

        assertTrue(UnarchiveRecurringUseCaseImpl(repository)(target).isRight())

        assertEquals(listOf(false), repository.updated.map { it.isArchived })
    }
}
