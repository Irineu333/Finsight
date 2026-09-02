package com.neoutils.finsight.analytics

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.analytics.MAX_PARAM_VALUE_LENGTH

/**
 * What actually goes on the wire, cut where the codebase can state it rather than in a
 * backend that cuts in silence. A list-valued parameter is already cut at an element
 * boundary by `putList`; this is the backstop for every other value, free text the user
 * typed above all — a category name has no length limit anywhere in this app.
 */
internal fun Event.wireParams(): Map<String, String> =
    params.mapValues { (_, value) -> value.take(MAX_PARAM_VALUE_LENGTH) }
