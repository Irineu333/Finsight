package com.neoutils.finsight.ui.modal.editTransaction

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.form.TransactionForm
import kotlinx.datetime.YearMonth

sealed class EditTransactionAction {
    data class SelectCreditCard(val creditCard: CreditCard?) : EditTransactionAction()
    data class SelectInvoiceMonth(val dueMonth: YearMonth) : EditTransactionAction()
    data class SelectAccount(val account: Account?) : EditTransactionAction()
    data class Submit(val form: TransactionForm) : EditTransactionAction()
}
