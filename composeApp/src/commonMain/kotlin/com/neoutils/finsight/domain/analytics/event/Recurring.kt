package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.RecurringForm

class CreateRecurring(params: Map<String, String>) : Event("create_recurring", params) {
    constructor(form: RecurringForm) : this(
        buildMap {
            put("type", form.type.name.lowercase())
            put("target", if (form.creditCard != null) "credit_card" else "account")
            form.category?.let { put("category", it.name) }
        }
    )
}

class EditRecurring(params: Map<String, String>) : Event("edit_recurring", params) {
    constructor(form: RecurringForm) : this(
        buildMap {
            put("type", form.type.name.lowercase())
            put("target", if (form.creditCard != null) "credit_card" else "account")
            form.category?.let { put("category", it.name) }
        }
    )
}

class DeleteRecurring(params: Map<String, String>) : Event("delete_recurring", params) {
    constructor(recurring: Recurring) : this(
        buildMap {
            put("type", recurring.type.name.lowercase())
            put("target", if (recurring.creditCard != null) "credit_card" else "account")
            recurring.category?.let { put("category", it.name) }
        }
    )
}

class ConfirmRecurring(params: Map<String, String>) : Event("confirm_recurring", params) {
    constructor(recurring: Recurring, target: Transaction.Target) : this(
        buildMap {
            put("type", recurring.type.name.lowercase())
            put("target", target.name.lowercase())
            recurring.category?.let { put("category", it.name) }
        }
    )
}

class SkipRecurring(params: Map<String, String>) : Event("skip_recurring", params) {
    constructor(recurring: Recurring, target: Transaction.Target) : this(
        buildMap {
            put("type", recurring.type.name.lowercase())
            put("target", target.name.lowercase())
            recurring.category?.let { put("category", it.name) }
        }
    )
}

class StopRecurring(params: Map<String, String>) : Event("stop_recurring", params) {
    constructor(recurring: Recurring) : this(
        buildMap {
            put("type", recurring.type.name.lowercase())
            put("target", if (recurring.creditCard != null) "credit_card" else "account")
            recurring.category?.let { put("category", it.name) }
        }
    )
}

class ReactivateRecurring(params: Map<String, String>) : Event("reactivate_recurring", params) {
    constructor(recurring: Recurring) : this(
        buildMap {
            put("type", recurring.type.name.lowercase())
            put("target", if (recurring.creditCard != null) "credit_card" else "account")
            recurring.category?.let { put("category", it.name) }
        }
    )
}
