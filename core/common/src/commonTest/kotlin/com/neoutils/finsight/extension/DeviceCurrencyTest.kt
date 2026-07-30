package com.neoutils.finsight.extension

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The mechanism half of resolving a base currency, on this target: that the platform is
 * asked at all, and answers with something an ISO code could be.
 *
 * It cannot assert *which* currency without pinning the machine's locale, and pinning it
 * would test the JDK rather than this. What it does pin is that the same door
 * [CurrencyFormatter] uses to find the locale is the one being used to decide.
 */
class DeviceCurrencyTest {

    @Test
    fun `the device names a currency, and it looks like an ISO code`() {
        val code = deviceCurrencyCode()

        assertNotNull(code, "A currency formatter on a real locale always names a currency.")
        assertTrue(
            code.length == 3 && code.all { it.isUpperCase() },
            "Expected an ISO 4217 code, got '$code'.",
        )
    }
}
