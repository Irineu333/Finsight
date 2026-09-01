package com.neoutils.finsight.domain.analytics

abstract class Event(val name: String, val params: Map<String, String> = emptyMap())

/**
 * What the backend accepts in a single parameter value. Firebase caps it at 100
 * characters and enforces the cap by dropping the rest in silence, so the number lives
 * here — stated once, where both the events that build a value and the transport that
 * sends it can read it.
 */
internal const val MAX_PARAM_VALUE_LENGTH = 100

/**
 * A list-valued parameter, in the one shape that survives the cap: the elements joined
 * by comma, cut at an **element boundary**, plus a `<key>_count` stating how many there
 * were. Cutting mid-element would publish a name that does not exist, and omitting the
 * count would make a cut list indistinguishable from a short one — which is exactly the
 * question these parameters are logged to answer.
 */
internal fun MutableMap<String, String>.putList(key: String, values: List<String>) {
    put("${key}_count", values.size.toString())
    put(key, values.joinToFit(MAX_PARAM_VALUE_LENGTH))
}

private fun List<String>.joinToFit(limit: Int): String {
    val joined = StringBuilder()
    for (value in this) {
        val addition = if (joined.isEmpty()) value else ",$value"
        if (joined.length + addition.length > limit) break
        joined.append(addition)
    }
    return joined.toString()
}
