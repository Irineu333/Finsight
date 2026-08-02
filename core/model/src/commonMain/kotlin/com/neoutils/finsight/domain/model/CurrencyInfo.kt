package com.neoutils.finsight.domain.model

/**
 * One currency the app offers: its ISO 4217 code, the glyph a form shows beside it, and
 * the name the user reads.
 *
 * The **code** is the whole of what the ledger persists — `accounts.currency` and
 * `entries.currency` are plain ISO strings, and it knows nothing else about them.
 * Everything here is presentation the ledger must not have an opinion on.
 *
 * **[name] is nullable, and `null` means "the platform names it".** A stored name would
 * be frozen in the language it was written in, so a row keeps one only when the *user*
 * wrote it. A reader that has to render a name uses `name ?: code`, which is also the
 * worst case of asking the platform.
 */
data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val name: String?,
)
