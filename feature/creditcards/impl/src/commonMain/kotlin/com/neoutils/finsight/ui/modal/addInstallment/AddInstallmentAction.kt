package com.neoutils.finsight.ui.modal.addInstallment

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import kotlinx.datetime.YearMonth

sealed class AddInstallmentAction {
    data class ChangeTitle(val title: String) : AddInstallmentAction()
    data class ChangeAmount(val amount: String) : AddInstallmentAction()
    data class ChangeDate(val date: String) : AddInstallmentAction()
    data class ChangeInstallments(val installments: Int) : AddInstallmentAction()
    data class SelectCategory(val category: Category?) : AddInstallmentAction()
    data class SelectCreditCard(val creditCard: CreditCard?) : AddInstallmentAction()
    data class NavigateToMonth(val dueMonth: YearMonth) : AddInstallmentAction()

    /** Carries no form: the ViewModel holds it. */
    data object Submit : AddInstallmentAction()
}
