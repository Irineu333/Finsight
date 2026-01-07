package com.neoutils.finance.extension

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction

fun Category.Type.isAccept(type: Transaction.Type): Boolean {
    return when (this) {
        Category.Type.EXPENSE -> type.isExpense
        Category.Type.INCOME -> type.isIncome
    }
}