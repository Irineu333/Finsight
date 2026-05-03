package com.neoutils.finsight.feature.transactions.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.core.domain.form.TransactionForm

class EditTransaction(params: Map<String, String>) : Event("edit_transaction", params) {
    constructor(form: TransactionForm) : this(
        buildMap {
            put("type", form.type.name.lowercase())
            put("target", form.target.name.lowercase())
            form.category?.let { put("category", it.name) }
        }
    )
}