package com.neoutils.finance.ui.screen.transactions

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction

data class TransactionsFilters(
    val category: Category? = null,
    val type: Transaction.Type? = null,
    val target: Transaction.Target? = null
)
