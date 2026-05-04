package com.neoutils.finsight.feature.installments.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.installments.model.Installment
import com.neoutils.finsight.feature.transactions.model.Operation

class DeleteInstallments(params: Map<String, String>) : Event("delete_installments", params) {
    constructor(installment: Installment, operations: List<Operation>) : this(
        buildMap {
            operations.firstOrNull()?.categoryId?.let { put("categoryId", it.toString()) }
            put("installments_count", installment.count.toString())
        }
    )
}
