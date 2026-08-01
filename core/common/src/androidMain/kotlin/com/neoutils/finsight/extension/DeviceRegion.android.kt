package com.neoutils.finsight.extension

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Currency
import java.util.Locale

/**
 * Android's region, read from the network the device is on rather than from the list of
 * languages it is read in.
 *
 * `Locale.getDefault()` is the top of the system language list, and Android has no
 * language without a country: picking *English (United States)* sets `en-US`, whatever
 * the user's money is in. So the locale is not consulted here at all — the SIM's home
 * country comes first, the country of the network currently serving the device second,
 * and when there is neither (a tablet, a Wi-Fi-only device, an emulator) the answer is
 * `null` and nothing is relabelled.
 *
 * Both reads are free of permissions. Neither is offered as a fallback to the other's
 * absence: `simCountryIso` says where the subscription is from and `networkCountryIso`
 * says where the device is now, and either is a statement about location, which the
 * language list is not.
 */
internal class TelephonyDeviceRegion(
    private val context: Context,
) : DeviceRegion {

    override fun currencyCode(): String? = runCatching {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null

        val region = telephony.simCountryIso
            .orEmpty()
            .ifBlank { telephony.networkCountryIso.orEmpty() }
            .takeIf { it.isNotBlank() }
            ?: return null

        Currency.getInstance(Locale.Builder().setRegion(region.uppercase()).build()).currencyCode
    }.getOrNull()
}
