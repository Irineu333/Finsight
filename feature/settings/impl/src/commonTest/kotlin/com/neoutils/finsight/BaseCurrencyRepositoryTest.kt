package com.neoutils.finsight

import com.neoutils.finsight.database.repository.BaseCurrencyRepository
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseCurrencyRepositoryTest {

    @Test
    fun `a persisted base is read back untouched`() {
        val settings = MapSettings("base_currency" to "EUR")

        assertEquals("EUR", BaseCurrencyRepository(settings).observe().value)
    }

    /**
     * The whole point of seeding on **absence** rather than on the creation of the
     * first account: the already-installed app never creates one, and would otherwise
     * never resolve a base at all.
     */
    @Test
    fun `an absent base is resolved once and persisted`() {
        val settings = MapSettings()

        val resolved = BaseCurrencyRepository(settings).observe().value

        assertEquals(resolved, settings.getStringOrNull("base_currency"))
        assertTrue(CurrencyCatalog.of(resolved) != null, "seeded a currency the app does not offer")
    }

    /**
     * A trip abroad changes the locale. It must not change this — moving the base
     * silently re-expresses every consolidated figure in the history.
     */
    @Test
    fun `a resolved base does not move when the app is opened again`() {
        val settings = MapSettings()

        val first = BaseCurrencyRepository(settings).observe().value
        val second = BaseCurrencyRepository(settings).observe().value

        assertEquals(first, second)
    }

    @Test
    fun `setting a base the app does not offer falls back rather than storing it`() = runTest {
        val settings = MapSettings("base_currency" to "BRL")
        val repository = BaseCurrencyRepository(settings)

        repository.set("XYZ")

        assertEquals(CurrencyCatalog.FALLBACK_CURRENCY, repository.observe().value)
        assertEquals(CurrencyCatalog.FALLBACK_CURRENCY, settings.getStringOrNull("base_currency"))
    }
}
