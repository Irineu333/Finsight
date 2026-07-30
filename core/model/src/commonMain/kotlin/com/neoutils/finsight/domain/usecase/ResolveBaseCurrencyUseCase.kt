package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.domain.model.LAST_RESORT_CURRENCY
import com.neoutils.finsight.extension.deviceCurrencyCode

/**
 * What currency this user lives in, decided **once**, from the device's locale.
 *
 * Mechanism and policy are split on purpose: naming the locale's currency is the platform's
 * job ([deviceCurrencyCode]), and deciding whether the app can honour that answer is this
 * layer's, because the catalog of what is offered lives here. A code the app does not offer
 * is not a failure to report — it is a question with a declared fallback.
 *
 * Nothing here persists anything. Resolving is what this use case does; resolving *once* is
 * a property of who calls it, and the base currency preference is what holds that.
 */
class ResolveBaseCurrencyUseCase(
    private val deviceCurrency: () -> String? = ::deviceCurrencyCode,
) {

    operator fun invoke(): String = deviceCurrency()
        ?.takeIf(CurrencyCatalog::offers)
        ?: LAST_RESORT_CURRENCY
}
