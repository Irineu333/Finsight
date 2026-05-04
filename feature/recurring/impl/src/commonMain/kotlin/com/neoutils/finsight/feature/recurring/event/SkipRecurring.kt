package com.neoutils.finsight.feature.recurring.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.transactions.model.Transaction

class SkipRecurring(params: Map<String, String>) : Event("skip_recurring", params) {
    constructor(recurring: Recurring, target: Transaction.Target) : this(
        buildMap {
            put("type", recurring.type.name.lowercase())
            put("target", target.name.lowercase())
            recurring.categoryId?.let { put("categoryId", it.toString()) }
        }
    )
}