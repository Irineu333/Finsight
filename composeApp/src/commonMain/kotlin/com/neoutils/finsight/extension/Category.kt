package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction

fun Category.Type.isAccept(type: Transaction.Type): Boolean {
    return when (this) {
        Category.Type.EXPENSE -> type.isExpense
        Category.Type.INCOME -> type.isIncome
    }
}

fun Category.Type.isAccept(type: Recurring.Type): Boolean {
    return when (this) {
        Category.Type.EXPENSE -> type.isExpense
        Category.Type.INCOME -> type.isIncome
    }
}