package com.neoutils.finsight.util

/**
 * Reads back what a user typed into a rate field.
 *
 * Both separators are accepted because the keyboard a device offers does not have to
 * agree with the app's language, and a rate refused for a comma is a refusal the user
 * cannot act on. `null` means "not a rate", which is what disables the submit button.
 *
 * It stays here, while `formatRate` lives in `:core:common`, because only the screen
 * that *edits* a rate ever parses one: an operation carries no rate field anywhere on
 * its path (design D6/D11), so the three cross-currency flows read rates and never
 * type them.
 */
fun String.toRateOrNull(): Double? {
    val normalized = trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    return value.takeIf { it > 0.0 && it.isFinite() }
}
