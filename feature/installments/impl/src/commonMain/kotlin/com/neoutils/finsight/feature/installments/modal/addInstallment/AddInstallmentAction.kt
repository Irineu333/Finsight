package com.neoutils.finsight.feature.installments.modal.addInstallment

import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.core.domain.form.TransactionForm
import kotlinx.datetime.YearMonth

sealed class AddInstallmentAction {
    data class SelectCreditCard(val creditCard: CreditCard?) : AddInstallmentAction()
    data class NavigateToMonth(val dueMonth: YearMonth) : AddInstallmentAction()
    data class Submit(
        val form: TransactionForm,
        val installments: Int,
    ) : AddInstallmentAction()
}
