package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm

class CreateInstallments(params: Map<String, String>) : Event("create_installments", params) {
    constructor(form: TransactionForm, count: Int) : this(
        buildMap {
            form.category?.let { put("category", it.name) }
            put("installments_count", count.toString())
        }
    )
}

class DeleteInstallments(params: Map<String, String>) : Event("delete_installments", params) {
    constructor(installment: Installment, categoryName: String?) : this(
        buildMap {
            categoryName?.let { put("category", it) }
            put("installments_count", installment.count.toString())
        }
    )
}
