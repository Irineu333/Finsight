package com.neoutils.finsight.ui.modal.recurringForm

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Transaction

sealed class RecurringFormAction {
    data class TypeChanged(val type: Transaction.Type) : RecurringFormAction()
    data class CategorySelected(val category: Category?) : RecurringFormAction()
    data class AccountSelected(val account: Account?) : RecurringFormAction()
    data class CreditCardSelected(val creditCard: CreditCard?) : RecurringFormAction()
    data class Save(val target: Transaction.Target) : RecurringFormAction()
}
