package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm

class CreateTransaction(params: Map<String, String>) : Event("create_transaction", params) {
    constructor(form: TransactionForm) : this(
        buildMap {
            put("type", form.type.name.lowercase())
            put("target", form.target.name.lowercase())
            form.category?.let { put("category", it.name) }
        }
    )
}

class EditTransaction(params: Map<String, String>) : Event("edit_transaction", params) {
    constructor(form: TransactionForm) : this(
        buildMap {
            put("type", form.type.name.lowercase())
            put("target", form.target.name.lowercase())
            form.category?.let { put("category", it.name) }
        }
    )
}

class DeleteTransaction(params: Map<String, String>) : Event("delete_transaction", params) {
    constructor(transaction: Transaction) : this(
        buildMap {
            put("type", transaction.type.name.lowercase())
            put("target", transaction.target.name.lowercase())
            transaction.category?.let { put("category", it.name) }
        }
    )
}
