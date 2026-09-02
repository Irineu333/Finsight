package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event

/**
 * The base currency the app expresses every consolidated figure in. Switching writes a
 * preference and nothing else — no row moves — which is exactly why it is worth logging:
 * nothing else in the data would show it happened.
 */
class SwitchBaseCurrency(params: Map<String, String>) : Event("switch_base_currency", params) {
    constructor(code: String) : this(mapOf("code" to code))
}

/**
 * @param isCustom whether the code names a unit the platform does not know. Registering
 * an invented unit is what the form exists to allow, and this is the only place that says
 * whether anyone does it.
 */
class CreateCurrency(params: Map<String, String>) : Event("create_currency", params) {
    constructor(code: String, isCustom: Boolean) : this(
        mapOf("code" to code, "is_custom" to isCustom.toString())
    )
}

class EditCurrency(params: Map<String, String>) : Event("edit_currency", params) {
    constructor(code: String) : this(mapOf("code" to code))
}

/** The row is gone. Retiring one that must be preserved is [ArchiveCurrency] instead. */
class DeleteCurrency(params: Map<String, String>) : Event("delete_currency", params) {
    constructor(code: String) : this(mapOf("code" to code))
}

/** Retired but kept, and reversible by [UnarchiveCurrency] — not a deletion. */
class ArchiveCurrency(params: Map<String, String>) : Event("archive_currency", params) {
    constructor(code: String) : this(mapOf("code" to code))
}

class UnarchiveCurrency(params: Map<String, String>) : Event("unarchive_currency", params) {
    constructor(code: String) : this(mapOf("code" to code))
}

/**
 * A rate the user stated by hand. The pair is carried in the direction it was observed
 * in — the archive never orders or inverts one (design D2), so neither does this.
 */
class CreateExchangeRate(params: Map<String, String>) : Event("create_exchange_rate", params) {
    constructor(from: String, to: String) : this(pairParams(from, to))
}

class EditExchangeRate(params: Map<String, String>) : Event("edit_exchange_rate", params) {
    constructor(from: String, to: String) : this(pairParams(from, to))
}

class DeleteExchangeRate(params: Map<String, String>) : Event("delete_exchange_rate", params) {
    constructor(from: String, to: String) : this(pairParams(from, to))
}

private fun pairParams(from: String, to: String) = mapOf("pair" to "${from}_$to")
