package com.neoutils.finsight.feature.recurring.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.recurring.model.form.RecurringForm

class CreateRecurring(params: Map<String, String>) : Event("create_recurring", params) {
    constructor(form: RecurringForm) : this(
        buildMap {
            put("type", form.type.name.lowercase())
            put("target", if (form.creditCard != null) "credit_card" else "account")
            form.category?.let { put("category", it.name) }
        }
    )
}