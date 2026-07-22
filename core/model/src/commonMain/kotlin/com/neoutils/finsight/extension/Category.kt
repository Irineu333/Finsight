package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType

fun Category.Type.isAccept(type: TransactionType): Boolean {
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

/**
 * How the write boundary must complete a one-sided intent of this [type]: on the
 * nominal of the category's own nature, tagged with its dimension.
 *
 * One owner, because it is one rule — "which nominal does this leg land on" is
 * derivable from the transaction type and the category, and nothing else. It was
 * written out twice, identically, in the manual and the recurring paths; a fix to
 * either would have missed the other.
 *
 * With no category the leg's own type decides the nature and the leg stays
 * unclassified, which is what "no category" means — there is no bucket standing in
 * for it.
 */
fun contraLegFor(type: TransactionType, category: Category?): ContraLeg = when (type) {
    TransactionType.ADJUSTMENT -> ContraLeg(AccountType.EQUITY)
    TransactionType.EXPENSE -> ContraLeg(category?.type?.accountType ?: AccountType.EXPENSE, category?.dimensionId)
    TransactionType.INCOME -> ContraLeg(category?.type?.accountType ?: AccountType.INCOME, category?.dimensionId)
}
