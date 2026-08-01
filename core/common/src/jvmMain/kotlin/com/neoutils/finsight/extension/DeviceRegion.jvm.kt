package com.neoutils.finsight.extension

import java.util.Currency
import java.util.Locale

/**
 * The desktop's region, which the JVM takes from the operating system's *region* setting
 * and not from the interface language — macOS reads `AppleLocale`, Windows the user
 * locale, and both are chosen apart from the language.
 *
 * The one platform where they are not chosen apart is a Linux session, where the country
 * is the territory of `LANG`. That is still a country the user typed, not a language list
 * Android built for them, and the desktop app is the one place where the same person's
 * database also exists on a phone — so the conservative read stays the same as the phone's
 * would be: a country the environment states, or nothing.
 */
internal class LocaleDeviceRegion : DeviceRegion {

    override fun currencyCode(): String? = runCatching {
        val region = Locale.getDefault().country.takeIf { it.isNotBlank() } ?: return null
        Currency.getInstance(Locale.Builder().setRegion(region).build()).currencyCode
    }.getOrNull()
}
