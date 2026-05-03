package com.neoutils.finsight.feature.installments.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.core.domain.form.TransactionForm

class CreateInstallments(params: Map<String, String>) : Event("create_installments", params) {
    constructor(form: TransactionForm, count: Int) : this(
        buildMap {
            form.category?.let { put("category", it.name) }
            put("installments_count", count.toString())
        }
    )
}