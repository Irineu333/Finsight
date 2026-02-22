package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction

data class TransactionsFilters(
    val category: Category? = null,
    val type: Transaction.Type? = null,
    val target: Transaction.Target? = null
)
