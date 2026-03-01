package com.neoutils.finsight.domain.model.form

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Transaction

data class RecurringForm(
    val type: Transaction.Type,
    val amount: String,
    val title: String,
    val dayOfMonth: String,
    val account: Account?,
    val creditCard: CreditCard?,
    val category: Category?,
) {
    fun isValid(): Boolean {
        if (amount.filter { it.isDigit() }.toLongOrNull()?.let { it > 0 } != true) return false
        if (title.isEmpty() && category == null) return false
        if (dayOfMonth.toIntOrNull()?.let { it in 1..31 } != true) return false
        if (type.isIncome && account == null) return false
        if (type.isExpense && account == null && creditCard == null) return false
        return true
    }
}
