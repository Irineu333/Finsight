package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget

data class TransactionsFilters(
    val category: Category? = null,
    val label: TransactionLabel? = null,
    val target: TransactionTarget? = null,
    val recurringOnly: Boolean = false,
    val installmentOnly: Boolean = false,
)
