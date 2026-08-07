package com.neoutils.finsight.ui.modal.editTransaction

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import kotlinx.datetime.YearMonth

sealed class EditTransactionAction {
    data class ChangeType(val type: TransactionType) : EditTransactionAction()
    data class ChangeTarget(val target: TransactionTarget) : EditTransactionAction()
    data class ChangeTitle(val title: String) : EditTransactionAction()
    data class ChangeAmount(val amount: String) : EditTransactionAction()
    data class ChangeDate(val date: String) : EditTransactionAction()
    data class SelectCategory(val category: Category?) : EditTransactionAction()
    data class SelectCreditCard(val creditCard: CreditCard?) : EditTransactionAction()
    data class SelectInvoiceMonth(val dueMonth: YearMonth) : EditTransactionAction()
    data class SelectAccount(val account: Account?) : EditTransactionAction()

    /** Carries no form: the ViewModel holds it. */
    data object Submit : EditTransactionAction()
}
