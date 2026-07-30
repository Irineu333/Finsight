package com.neoutils.finsight.domain.model

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currency_name_aed
import com.neoutils.finsight.resources.currency_name_ars
import com.neoutils.finsight.resources.currency_name_aud
import com.neoutils.finsight.resources.currency_name_brl
import com.neoutils.finsight.resources.currency_name_cad
import com.neoutils.finsight.resources.currency_name_chf
import com.neoutils.finsight.resources.currency_name_cny
import com.neoutils.finsight.resources.currency_name_dkk
import com.neoutils.finsight.resources.currency_name_eur
import com.neoutils.finsight.resources.currency_name_gbp
import com.neoutils.finsight.resources.currency_name_ils
import com.neoutils.finsight.resources.currency_name_inr
import com.neoutils.finsight.resources.currency_name_mxn
import com.neoutils.finsight.resources.currency_name_nok
import com.neoutils.finsight.resources.currency_name_nzd
import com.neoutils.finsight.resources.currency_name_pen
import com.neoutils.finsight.resources.currency_name_pln
import com.neoutils.finsight.resources.currency_name_sek
import com.neoutils.finsight.resources.currency_name_try
import com.neoutils.finsight.resources.currency_name_usd
import com.neoutils.finsight.resources.currency_name_uyu
import com.neoutils.finsight.resources.currency_name_zar
import com.neoutils.finsight.util.UiText

/**
 * One currency the app is willing to denominate an account in: its ISO 4217 code,
 * the glyph a form shows beside it, and the name the user reads.
 *
 * The **code** is the whole of what is persisted — `accounts.currency` and
 * `entries.currency` are plain ISO strings, and the ledger knows nothing else about
 * them. Everything here is presentation the ledger must not have an opinion on.
 */
data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val name: UiText,
)

/**
 * The currencies the app offers, restricted to those with **two** decimal places.
 *
 * The restriction is a **deliberate premise**, not an oversight: every piece of
 * money arithmetic in this app assumes base 100 — `(amount * 100).roundToLong()` on
 * the way in, a `Double` read boundary on the way out, and `MoneyInputTransformation`
 * in between. Supporting JPY (0 places) or KWD (3) is not adding a field; it is
 * redoing the whole `Double`↔cents conversion of the ledger and of the UI, which
 * would mix two changes of very different risk.
 *
 * This is the app's opinion about which currencies it supports, so it lives here
 * rather than in `:core:ledger` — the ledger knows that a currency exists and
 * nothing whatsoever about which ones.
 */
object CurrencyCatalog {

    /**
     * The currency of **last resort** — not a default.
     *
     * It replaces the ledger's old `BASE_CURRENCY`, and the demotion is the point:
     * the currency of a new account and the base currency of consolidation are both
     * resolved from the device locale (design D28). This is only what answers when
     * the locale's currency is not one of the ones offered above.
     */
    const val FALLBACK_CURRENCY: String = "USD"

    val currencies: List<CurrencyInfo> = listOf(
        CurrencyInfo("AED", "د.إ", UiText.Res(Res.string.currency_name_aed)),
        CurrencyInfo("ARS", "$", UiText.Res(Res.string.currency_name_ars)),
        CurrencyInfo("AUD", "A$", UiText.Res(Res.string.currency_name_aud)),
        CurrencyInfo("BRL", "R$", UiText.Res(Res.string.currency_name_brl)),
        CurrencyInfo("CAD", "C$", UiText.Res(Res.string.currency_name_cad)),
        CurrencyInfo("CHF", "CHF", UiText.Res(Res.string.currency_name_chf)),
        CurrencyInfo("CNY", "¥", UiText.Res(Res.string.currency_name_cny)),
        CurrencyInfo("DKK", "kr", UiText.Res(Res.string.currency_name_dkk)),
        CurrencyInfo("EUR", "€", UiText.Res(Res.string.currency_name_eur)),
        CurrencyInfo("GBP", "£", UiText.Res(Res.string.currency_name_gbp)),
        CurrencyInfo("ILS", "₪", UiText.Res(Res.string.currency_name_ils)),
        CurrencyInfo("INR", "₹", UiText.Res(Res.string.currency_name_inr)),
        CurrencyInfo("MXN", "MX$", UiText.Res(Res.string.currency_name_mxn)),
        CurrencyInfo("NOK", "kr", UiText.Res(Res.string.currency_name_nok)),
        CurrencyInfo("NZD", "NZ$", UiText.Res(Res.string.currency_name_nzd)),
        CurrencyInfo("PEN", "S/", UiText.Res(Res.string.currency_name_pen)),
        CurrencyInfo("PLN", "zł", UiText.Res(Res.string.currency_name_pln)),
        CurrencyInfo("SEK", "kr", UiText.Res(Res.string.currency_name_sek)),
        CurrencyInfo("TRY", "₺", UiText.Res(Res.string.currency_name_try)),
        CurrencyInfo("USD", "US$", UiText.Res(Res.string.currency_name_usd)),
        CurrencyInfo("UYU", "\$U", UiText.Res(Res.string.currency_name_uyu)),
        CurrencyInfo("ZAR", "R", UiText.Res(Res.string.currency_name_zar)),
    )

    private val byCode: Map<String, CurrencyInfo> = currencies.associateBy { it.code }

    /** The offered currency with this code, or `null` when the app does not offer it. */
    fun of(code: String?): CurrencyInfo? = code?.let { byCode[it.uppercase()] }

    /**
     * Reduces a currency code of unknown provenance — a device locale's, in practice
     * — to one the app can actually denominate an account in, falling back to
     * [FALLBACK_CURRENCY] when it cannot.
     *
     * It is the second half of resolving the locale, and it lives here rather than
     * beside the locale reader because the two are different jobs with different
     * owners: `:core:common` knows what the device says, and this module knows what
     * the app accepts. It is also what lets the migration of design D30 receive a
     * currency code already resolved *and* validated, without ever naming a locale
     * or a catalog.
     */
    fun reduce(code: String?): String = of(code)?.code ?: FALLBACK_CURRENCY

    /** The glyph a form shows for a code, falling back to the code itself. */
    fun symbolOf(code: String): String = of(code)?.symbol ?: code
}
