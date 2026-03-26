package com.neoutils.finsight.ui.modal.addInstallment

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.form.TransactionForm
import kotlinx.datetime.YearMonth

sealed class AddInstallmentAction {
    data class SelectCreditCard(val creditCard: CreditCard?) : AddInstallmentAction()
    data class NavigateToMonth(val dueMonth: YearMonth) : AddInstallmentAction()
    data class Submit(
        val form: TransactionForm,
        val installments: Int,
    ) : AddInstallmentAction()
}
