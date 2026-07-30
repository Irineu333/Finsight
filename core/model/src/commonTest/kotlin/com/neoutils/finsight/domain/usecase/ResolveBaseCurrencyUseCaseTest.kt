package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.LAST_RESORT_CURRENCY
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The policy half of resolving a base currency: what the app does with whatever the device
 * says. The mechanism half — asking the platform — is proven per target, since only there
 * is there a real locale to ask.
 */
class ResolveBaseCurrencyUseCaseTest {

    @Test
    fun `a locale whose currency the app offers decides the base`() {
        assertEquals("EUR", resolve("EUR"))
    }

    @Test
    fun `a locale of a currency the app does not offer falls back to the declared one`() {
        // Yen: a real currency, and one of zero decimal places — the exact case the curated
        // catalog exists to keep out, since the app holds every amount at base 100.
        assertEquals(LAST_RESORT_CURRENCY, resolve("JPY"))
    }

    @Test
    fun `a platform that names no currency falls back to the declared one`() {
        assertEquals(LAST_RESORT_CURRENCY, resolve(null))
    }

    @Test
    fun `the fallback is last resort, not a default the offered set could reach past`() {
        // Stated as its own case because the fallback reads like a default until you see
        // that a locale the catalog *does* offer never touches it.
        assertEquals("USD", resolve("USD"))
    }

    private fun resolve(deviceCurrency: String?) =
        ResolveBaseCurrencyUseCase(deviceCurrency = { deviceCurrency })()
}
