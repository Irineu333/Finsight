package com.neoutils.finsight.feature.recurring.extension

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.feature.recurring.model.Recurring

fun Category.Type.isAccept(type: Recurring.Type): Boolean {
    return when (this) {
        Category.Type.EXPENSE -> type.isExpense
        Category.Type.INCOME -> type.isIncome
    }
}
