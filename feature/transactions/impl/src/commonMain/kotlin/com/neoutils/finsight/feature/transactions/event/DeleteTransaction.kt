package com.neoutils.finsight.feature.transactions.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.transactions.model.Transaction

class DeleteTransaction(params: Map<String, String>) : Event("delete_transaction", params) {
    constructor(transaction: Transaction) : this(
        buildMap {
            put("type", transaction.type.name.lowercase())
            put("target", transaction.target.name.lowercase())
            transaction.categoryId?.let { put("categoryId", it.toString()) }
        }
    )
}