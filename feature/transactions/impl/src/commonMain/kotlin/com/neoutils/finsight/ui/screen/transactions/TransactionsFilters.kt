package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType

data class TransactionsFilters(
    val category: Category? = null,
    val type: TransactionType? = null,
    val target: TransactionTarget? = null,
    val recurringOnly: Boolean = false,
    val installmentOnly: Boolean = false,
)
