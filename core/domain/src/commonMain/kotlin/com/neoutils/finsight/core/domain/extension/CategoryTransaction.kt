package com.neoutils.finsight.core.domain.extension

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.domain.model.Transaction

fun Category.Type.isAccept(type: Transaction.Type): Boolean {
    return when (this) {
        Category.Type.EXPENSE -> type.isExpense
        Category.Type.INCOME -> type.isIncome
    }
}
