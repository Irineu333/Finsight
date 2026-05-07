@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.recurring.model.form

import com.neoutils.finsight.core.utils.extension.moneyToDouble
import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.recurring.model.Recurring
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class RecurringForm(
    val id: Long = 0L,
    val type: Recurring.Type = Recurring.Type.EXPENSE,
    val amount: String = "",
    val title: String = "",
    val dayOfMonth: String = "",
    val account: Account? = null,
    val creditCard: CreditCard? = null,
    val category: Category? = null,
    val createdAt: Long? = null,
    val isActive: Boolean = true,
) {
    fun isValid(): Boolean {
        if (amount.isEmpty()) return false
        if (amount.moneyToDouble() == 0.0) return false
        if (title.isEmpty() && category == null) return false
        if (dayOfMonth.toIntOrNull()?.let { it in 1..31 } != true) return false
        if (type.isIncome && account == null) return false
        if (type.isExpense && account == null && creditCard == null) return false
        return true
    }

    fun build(): Recurring = Recurring(
        id = id,
        type = type,
        amount = amount.moneyToDouble(),
        title = title.ifEmpty { null },
        dayOfMonth = dayOfMonth.toInt(),
        categoryId = category?.id,
        accountId = account?.id,
        creditCardId = if (type.isIncome) null else creditCard?.id,
        createdAt = createdAt ?: Clock.System.now().toEpochMilliseconds(),
        isActive = isActive,
    )
}