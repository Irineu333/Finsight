package com.neoutils.finsight.feature.recurring.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.transactions.model.Transaction

class ConfirmRecurring(params: Map<String, String>) : Event("confirm_recurring", params) {
    constructor(
        type: Recurring.Type,
        target: Transaction.Target,
        categoryId: Long?,
    ) : this(
        buildMap {
            put("type", type.name.lowercase())
            put("target", target.name.lowercase())
            categoryId?.let { put("categoryId", it.toString()) }
        }
    )
}
