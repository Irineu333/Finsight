package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.usecase.ResolveBaseCurrencyUseCase
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The base currency is resolved **once**, and the whole point of this suite is that "once"
 * survives the second run.
 *
 * Changing the device's region later must not move it. Moving it would silently restate
 * every consolidated figure in the user's history — a month closed in euros re-read in
 * pounds — because of a trip.
 */
class BaseCurrencyRepositoryTest {

    @Test
    fun `first run resolves the base from the locale and persists it`() {
        val settings = MapSettings()

        val repository = repository(settings, locale = "EUR")

        assertEquals("EUR", repository.current())
        assertEquals("EUR", settings.getStringOrNull("base_currency"))
    }

    @Test
    fun `a later change of locale does not move a base already resolved`() {
        val settings = MapSettings()
        repository(settings, locale = "EUR")

        val afterTravelling = repository(settings, locale = "USD")

        assertEquals("EUR", afterTravelling.current())
    }

    @Test
    fun `an app already installed resolves on first read, with no account created`() {
        // The already-installed case: nothing seeded the value, and no account creation is
        // going to happen, since the user has had accounts for months. Resolution hangs off
        // the absence of the stored value and nothing else.
        val settings = MapSettings()

        assertEquals("GBP", repository(settings, locale = "GBP").current())
    }

    @Test
    fun `setting the base emits it, so a figure on screen follows`() = runTest {
        val settings = MapSettings()
        val repository = repository(settings, locale = "BRL")

        repository.set("USD")

        assertEquals("USD", repository.observe().value)
        assertEquals("USD", settings.getStringOrNull("base_currency"))
    }

    private fun repository(settings: MapSettings, locale: String) = BaseCurrencyRepository(
        settings = settings,
        resolveBaseCurrency = ResolveBaseCurrencyUseCase(deviceCurrency = { locale }),
    )
}
