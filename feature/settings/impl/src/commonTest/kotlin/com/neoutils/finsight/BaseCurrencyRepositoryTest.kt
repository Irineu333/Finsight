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

    /**
     * Seeding resolves through the catalog, so a region the app does not offer lands on
     * the currency of last resort and *that* is what is persisted — never the code the
     * device named.
     */
    @Test
    fun `a region the app does not offer never reaches the persisted value`() {
        val settings = MapSettings()

        val resolved = BaseCurrencyRepository(settings).observe().value

        assertEquals(resolved, settings.getStringOrNull("base_currency"))
        assertTrue(
            CurrencyCatalog.of(resolved) != null,
            "persisted a currency the app does not offer",
        )
    }

    /** Switching is the write of a preference: it emits, and it persists. */
    @Test
    fun `switching emits and persists`() = runTest {
        val settings = MapSettings("base_currency" to "BRL")
        val repository = BaseCurrencyRepository(settings)

        repository.set("USD")

        assertEquals("USD", repository.observe().value)
        assertEquals("USD", settings.getStringOrNull("base_currency"))
    }

    /**
     * And what was written is what is read back. Re-seeding from the locale here would
     * undo the switch on the next launch, silently.
     */
    @Test
    fun `a switched base survives reopening and is not re-seeded`() = runTest {
        val settings = MapSettings()
        BaseCurrencyRepository(settings).set("JPY")

        assertEquals("JPY", BaseCurrencyRepository(settings).observe().value)
    }
}
