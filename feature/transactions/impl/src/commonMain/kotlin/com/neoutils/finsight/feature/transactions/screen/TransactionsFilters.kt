package com.neoutils.finsight.feature.transactions.screen

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.domain.model.Transaction

data class TransactionsFilters(
    val category: Category? = null,
    val type: Transaction.Type? = null,
    val target: Transaction.Target? = null,
    val recurringOnly: Boolean = false,
    val installmentOnly: Boolean = false,
)
