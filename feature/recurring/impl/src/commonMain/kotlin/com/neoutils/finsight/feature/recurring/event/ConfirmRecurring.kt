package com.neoutils.finsight.feature.recurring.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.core.domain.model.Recurring
import com.neoutils.finsight.core.domain.model.Transaction

class ConfirmRecurring(params: Map<String, String>) : Event("confirm_recurring", params) {
    constructor(recurring: Recurring, target: Transaction.Target) : this(
        buildMap {
            put("type", recurring.type.name.lowercase())
            put("target", target.name.lowercase())
            recurring.categoryId?.let { put("categoryId", it.toString()) }
        }
    )
}