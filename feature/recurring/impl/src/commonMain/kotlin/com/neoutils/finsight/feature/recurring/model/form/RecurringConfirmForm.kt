package com.neoutils.finsight.feature.recurring.model.form

import com.neoutils.finsight.core.utils.extension.moneyToDouble
import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.datetime.LocalDate

data class RecurringConfirmForm(
    val title: String?,
    val type: Recurring.Type,
    val category: Category?,
    val date: LocalDate,
    val amount: String,
    val target: Transaction.Target,
    val account: Account?,
    val creditCard: CreditCard?,
    val invoice: Invoice?,
) {
    val label: String get() = title?.takeIf { it.isNotBlank() } ?: "Untitled"

    fun isValid(): Boolean {
        if (amount.moneyToDouble() <= 0.0) return false
        return when {
            target.isCreditCard -> creditCard != null
            else -> account != null
        }
    }
}
