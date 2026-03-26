package com.neoutils.finsight.ui.modal.recurringForm

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.form.RecurringForm

sealed class RecurringFormAction {
    data class SelectAccount(val account: Account?) : RecurringFormAction()
    data class SelectCreditCard(val creditCard: CreditCard?) : RecurringFormAction()
    data class Submit(val form: RecurringForm) : RecurringFormAction()
}
