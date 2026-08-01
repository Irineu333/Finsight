package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.extension.localeCurrencyCode
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The base currency, seeded **once** from the device's locale and stable afterwards.
 *
 * Seeding happens on the **absence of the persisted value**, not on the creation of
 * the first account — which also covers the already-installed app, where
 * `EnsureDefaultAccountUseCase` returns early because an account exists. And it is
 * read once, in `init`: a later trip abroad changes the locale but not this, because
 * moving it would silently re-express every consolidated figure in the history
 * (design D28).
 *
 * **The locale, not `DeviceRegion`** — the two say different things and only one of them
 * belongs here (design D30). `DeviceRegion` exists for the legacy relabel, which rewrites
 * stored rows irreversibly and therefore may not be triggered by which language someone
 * reads; on Android the locale's country rides along with the language list, so it would
 * be. This is the other case: seeding a display preference from the very source the app
 * has always formatted money with, which for a single-currency user never reaches a
 * screen at all.
 *
 * **There is no write path**, and that is deliberate (design D18): v1 does not offer the
 * switch, and a setter that wrote the new code alone would leave every rate on file being
 * read against a base it was never measured in. What keeps offering it later cheap is
 * that nothing converted is persisted and every read already observes this flow — not a
 * setter sitting here unused.
 */
class BaseCurrencyRepository(
    private val settings: Settings,
) : IBaseCurrencyRepository {

    private val _currency = MutableStateFlow(seed())

    override fun observe(): StateFlow<String> = _currency

    private fun seed(): String {
        settings.getStringOrNull(KEY)?.let { return it }

        // The device says what it says; the catalog decides what the app accepts.
        // Anything it does not accept lands on the currency of last resort, which is
        // last resort and not a product default.
        val resolved = CurrencyCatalog.reduce(localeCurrencyCode())
        settings.putString(KEY, resolved)
        return resolved
    }

    companion object {
        private const val KEY = "base_currency"
    }
}
