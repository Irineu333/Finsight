package com.neoutils.finsight.feature.recurring.modal.recurringForm

import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.feature.recurring.model.form.RecurringForm
sealed class RecurringFormAction {
    data class SelectAccount(val account: Account?) : RecurringFormAction()
    data class SelectCreditCard(val creditCard: CreditCard?) : RecurringFormAction()
    data class Submit(val form: RecurringForm) : RecurringFormAction()
}
