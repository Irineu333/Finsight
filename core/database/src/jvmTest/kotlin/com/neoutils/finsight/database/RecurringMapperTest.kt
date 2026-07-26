package com.neoutils.finsight.database

import com.neoutils.finsight.database.entity.RecurringEntity
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The column says "active", the domain says "archived". This mapper is the only place
 * that translates between them (design D1), so the inversion is proven in both
 * directions — a one-sided fix would read as working until something round-trips.
 */
class RecurringMapperTest {

    private val mapper = RecurringMapper()

    private fun entity(isActive: Boolean) = RecurringEntity(
        id = 1L,
        type = RecurringEntity.Type.EXPENSE,
        amount = 100.0,
        title = "Rent",
        dayOfMonth = 5,
        categoryId = null,
        accountId = null,
        creditCardId = null,
        createdAt = 0L,
        isActive = isActive,
    )

    private fun domain(isArchived: Boolean) = Recurring(
        id = 1L,
        type = TransactionType.EXPENSE,
        amount = 100.0,
        title = "Rent",
        dayOfMonth = 5,
        category = null,
        account = null,
        creditCard = null,
        createdAt = 0L,
        isArchived = isArchived,
    )

    private fun toDomain(entity: RecurringEntity) =
        mapper.toDomain(entity = entity, category = null, account = null, creditCard = null)

    @Test
    fun `an active row reads as not archived`() {
        assertFalse(toDomain(entity(isActive = true)).isArchived)
    }

    @Test
    fun `an inactive row reads as archived`() {
        assertTrue(toDomain(entity(isActive = false)).isArchived)
    }

    @Test
    fun `an archived recurring writes an inactive row`() {
        assertFalse(mapper.toEntity(domain(isArchived = true)).isActive)
    }

    @Test
    fun `an unarchived recurring writes an active row`() {
        assertTrue(mapper.toEntity(domain(isArchived = false)).isActive)
    }

    @Test
    fun `the flag survives a round trip in both states`() {
        assertEquals(domain(isArchived = true), toDomain(mapper.toEntity(domain(isArchived = true))))
        assertEquals(domain(isArchived = false), toDomain(mapper.toEntity(domain(isArchived = false))))
    }
}
