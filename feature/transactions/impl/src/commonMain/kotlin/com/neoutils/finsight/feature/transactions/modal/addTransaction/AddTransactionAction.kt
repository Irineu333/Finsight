package com.neoutils.finsight.feature.transactions.modal.addTransaction

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.transactions.form.TransactionForm
import kotlinx.datetime.YearMonth

sealed class AddTransactionAction {
    data class SelectCreditCard(val creditCard: CreditCard?) : AddTransactionAction()
    data class SelectInvoiceMonth(val dueMonth: YearMonth) : AddTransactionAction()
    data class SelectAccount(val account: Account?) : AddTransactionAction()
    data class Submit(val form: TransactionForm) : AddTransactionAction()
}
