package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction

fun Category.Type.isAccept(type: Transaction.Type): Boolean {
    return when (this) {
        Category.Type.EXPENSE -> type.isExpense
        Category.Type.INCOME -> type.isIncome
    }
}

/**
 * The chart-of-accounts nature a category projects onto: an expense category is
 * an `EXPENSE` account, an income category an `INCOME` account. This is the
 * ledger-side expression of the [isAccept] coherence rule.
 */
val Category.Type.accountType: AccountType
    get() = when (this) {
        Category.Type.EXPENSE -> AccountType.EXPENSE
        Category.Type.INCOME -> AccountType.INCOME
    }