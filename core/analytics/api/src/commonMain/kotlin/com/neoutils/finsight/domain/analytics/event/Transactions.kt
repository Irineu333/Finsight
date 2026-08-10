package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.extension.deriveTransactionType
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
    // The wire keys and values are published format: `type`/`target` carry the
    // same constant names as before, now derived from the ledger instead of read
    // off a persisted leg.
    // [categoryName] is passed in rather than read off the transaction: the ledger
    // hands out a dimension, and only the categories feature knows what it names.
    constructor(transaction: Transaction, categoryName: String?) : this(
        buildMap {
            transaction.primaryEntry?.let { entry ->
                put("type", deriveTransactionType(entry.amount, transaction.entries).name.lowercase())
            }
            val target = if (transaction.hasLiabilityLeg) TransactionTarget.CREDIT_CARD else TransactionTarget.ACCOUNT
            put("target", target.name.lowercase())
            categoryName?.let { put("category", it) }
        }
    )
}
