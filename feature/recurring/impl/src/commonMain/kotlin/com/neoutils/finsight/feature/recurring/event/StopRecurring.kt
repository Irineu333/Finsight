package com.neoutils.finsight.feature.recurring.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.core.domain.model.Recurring

class StopRecurring(params: Map<String, String>) : Event("stop_recurring", params) {
    constructor(recurring: Recurring) : this(
        buildMap {
            put("type", recurring.type.name.lowercase())
            put("target", if (recurring.creditCard != null) "credit_card" else "account")
            recurring.category?.let { put("category", it.name) }
        }
    )
}