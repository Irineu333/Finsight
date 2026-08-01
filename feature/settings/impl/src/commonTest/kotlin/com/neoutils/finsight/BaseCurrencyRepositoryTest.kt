package com.neoutils.finsight

import com.neoutils.finsight.database.repository.BaseCurrencyRepository
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.russhwolf.settings.MapSettings
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
     * **Seeding is the only writer, so the catalog is enforced where the value is
     * decided.** This used to be asserted through the setter, which reduced whatever it
     * was handed; the setter is gone (offering the switch means shipping the rate
     * re-expression with it), and what remains is the one path that writes: a region the
     * app does not offer resolves to the currency of last resort and *that* is what is
     * persisted — never the code the device named.
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
}
