package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.model.FALLBACK_CURRENCY
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.extension.isTwoDecimalCurrency
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
 * **The locale, which is the source the app has always formatted money with** — the same
 * read the legacy relabel of design D30 makes, and deliberately so: both want what the
 * user has been *reading*, not where the user is. The difference between them is what
 * they do with it — this seeds a display preference the user can change, and which for a
 * single-currency user never reaches a screen at all.
 *
 * **The write path is two lines, and that is the whole switch** (design D5). Every rate
 * on file names both of its ends, so none of them changes meaning when this does; no
 * stored row is touched, no migration runs and nothing is re-expressed here. The whole
 * re-expression is a read, owned by `ExchangeRateRepository`.
 */
class BaseCurrencyRepository(
    private val settings: Settings,
) : IBaseCurrencyRepository {

    private val _currency = MutableStateFlow(seed())

    override fun observe(): StateFlow<String> = _currency

    override suspend fun set(code: String) {
        settings.putString(KEY, code)
        _currency.value = code
    }

    private fun seed(): String {
        settings.getStringOrNull(KEY)?.let { return it }

        // The seeding already wrote the locale's currency as a row, so there is no
        // curated set left to reduce to: what remains is the premise itself. A locale
        // currency of two decimal places *is* the base; anything else — no currency at
        // all, or one of zero or three places, the two cases the seeding skips — lands
        // on the currency of last resort, which is last resort and not a product
        // default.
        val resolved = localeCurrencyCode()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() && isTwoDecimalCurrency(it) }
            ?: FALLBACK_CURRENCY
        settings.putString(KEY, resolved)
        return resolved
    }

    companion object {
        private const val KEY = "base_currency"
    }
}
