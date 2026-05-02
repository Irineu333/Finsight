package com.neoutils.finsight.feature.installments.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.installments.model.Installment
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.form.TransactionForm
class CreateInstallments(params: Map<String, String>) : Event("create_installments", params) {
    constructor(form: TransactionForm, count: Int) : this(
        buildMap {
            form.category?.let { put("category", it.name) }
            put("installments_count", count.toString())
        }
    )
}

class DeleteInstallments(params: Map<String, String>) : Event("delete_installments", params) {
    constructor(installment: Installment, operations: List<Operation>) : this(
        buildMap {
            operations.firstOrNull()?.category?.let { put("category", it.name) }
            put("installments_count", installment.count.toString())
        }
    )
}
