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
        val repository = FakeRecurringRepository()

        assertTrue(ArchiveRecurringUseCase(repository)(recurring()).isRight())

        assertEquals(listOf(true), repository.updated.map { it.isArchived })
    }

    @Test
    fun `unarchiving clears the flag and is refused by no invariant`() = runTest {
        val repository = FakeRecurringRepository()

        assertTrue(
            UnarchiveRecurringUseCase(repository)(recurring(isArchived = true)).isRight()
        )

        assertEquals(listOf(false), repository.updated.map { it.isArchived })
    }
}
