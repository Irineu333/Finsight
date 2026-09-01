package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.recurring
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Archiving a recurring is what takes it out of circulation, and for a recurring
 * being in circulation *is* generating — so an archived one is never presented as
 * pending, no matter how far past its due day the month is.
 */
class GetPendingRecurringUseCaseTest {

    private val useCase = GetPendingRecurringUseCase(
        GetRecurringCyclesUseCase(GetUnhandledRecurringUseCase()),
    )

    // Both are due on day 5; today is the 20th, so both are past due.
    private val today = LocalDate(2026, 7, 20)
    private val active = recurring(id = 1L)
    private val archived = recurring(id = 2L, isArchived = true)

    @Test
    fun `an archived recurring is not pending`() {
        val pending = useCase(
            recurringList = listOf(active, archived),
            occurrences = emptyList(),
            today = today,
        )

        assertEquals(listOf(active.id), pending.map { it.id })
    }

    @Test
    fun `unarchiving brings it back to pending`() {
        val pending = useCase(
            recurringList = listOf(archived.copy(isArchived = false)),
            occurrences = emptyList(),
            today = today,
        )

        assertEquals(listOf(archived.id), pending.map { it.id })
    }
}
