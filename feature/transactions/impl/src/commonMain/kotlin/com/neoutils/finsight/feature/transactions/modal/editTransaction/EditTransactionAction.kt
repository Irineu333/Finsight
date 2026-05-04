package com.neoutils.finsight.feature.transactions.modal.editTransaction

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.transactions.form.TransactionForm
import kotlinx.datetime.YearMonth

sealed class EditTransactionAction {
    data class SelectCreditCard(val creditCard: CreditCard?) : EditTransactionAction()
    data class SelectInvoiceMonth(val dueMonth: YearMonth) : EditTransactionAction()
    data class SelectAccount(val account: Account?) : EditTransactionAction()
    data class Submit(val form: TransactionForm) : EditTransactionAction()
}
