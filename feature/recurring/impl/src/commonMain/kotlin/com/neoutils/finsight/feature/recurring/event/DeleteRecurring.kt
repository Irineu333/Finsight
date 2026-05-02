package com.neoutils.finsight.feature.recurring.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.recurring.model.Recurring

class DeleteRecurring(params: Map<String, String>) : Event("delete_recurring", params) {
    constructor(recurring: Recurring) : this(
        buildMap {
            put("type", recurring.type.name.lowercase())
            put("target", if (recurring.creditCard != null) "credit_card" else "account")
            recurring.category?.let { put("category", it.name) }
        }
    )
}