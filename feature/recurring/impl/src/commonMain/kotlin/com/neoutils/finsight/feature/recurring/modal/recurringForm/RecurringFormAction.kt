package com.neoutils.finsight.feature.recurring.modal.recurringForm

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.recurring.state.RecurringForm

sealed class RecurringFormAction {
    data class SelectAccount(val account: Account?) : RecurringFormAction()
    data class SelectCreditCard(val creditCard: CreditCard?) : RecurringFormAction()
    data class Submit(val form: RecurringForm) : RecurringFormAction()
}
