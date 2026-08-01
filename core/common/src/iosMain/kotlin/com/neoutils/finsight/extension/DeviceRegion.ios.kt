package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale

/**
 * iOS is the platform where the question was always answered correctly: *Region* is a
 * setting of its own, chosen apart from *Language*, and `NSLocale.currentLocale` carries
 * the currency of that region. Reading English on a device set to Brazil answers `BRL`.
 *
 * So this is the same read as [localeCurrencyCode] here, and deliberately not shared with
 * it: the two are one expression on this platform and two on the others, and collapsing
 * them again is how the distinction was lost the first time.
 */
internal class RegionDeviceRegion : DeviceRegion {

    override fun currencyCode(): String? = NSLocale.currentLocale.currencyCode
}
