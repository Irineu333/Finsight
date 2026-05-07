package com.neoutils.finsight.feature.recurring.modal.recurringForm

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.recurring.model.Recurring

sealed class RecurringFormAction {
    data class TypeChanged(val type: Recurring.Type) : RecurringFormAction()
    data class AmountChanged(val amount: String) : RecurringFormAction()
    data class TitleChanged(val title: String) : RecurringFormAction()
    data class DayOfMonthChanged(val dayOfMonth: String) : RecurringFormAction()
    data class SelectAccount(val account: Account?) : RecurringFormAction()
    data class SelectCreditCard(val creditCard: CreditCard?) : RecurringFormAction()
    data class SelectCategory(val category: Category?) : RecurringFormAction()
    data object Submit : RecurringFormAction()
}
