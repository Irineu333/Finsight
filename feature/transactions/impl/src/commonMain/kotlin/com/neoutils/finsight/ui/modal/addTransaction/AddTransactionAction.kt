package com.neoutils.finsight.ui.modal.addTransaction

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import kotlinx.datetime.YearMonth

sealed class AddTransactionAction {
    data class ChangeType(val type: TransactionType) : AddTransactionAction()
    data class ChangeTarget(val target: TransactionTarget) : AddTransactionAction()
    data class ChangeTitle(val title: String) : AddTransactionAction()
    data class ChangeAmount(val amount: String) : AddTransactionAction()
    data class ChangeDate(val date: String) : AddTransactionAction()
    data class ChangeInstallments(val installments: Int) : AddTransactionAction()
    data class SelectCategory(val category: Category?) : AddTransactionAction()
    data class SelectCreditCard(val creditCard: CreditCard?) : AddTransactionAction()
    data class SelectInvoiceMonth(val dueMonth: YearMonth) : AddTransactionAction()
    data class SelectAccount(val account: Account?) : AddTransactionAction()

    /**
     * Marks the transaction being written as one that repeats every month. Nothing is
     * written until [Submit]: this is form state, undone by unmarking or by leaving.
     */
    data class ChangeRecurring(val isRecurring: Boolean) : AddTransactionAction()

    /**
     * Carries no form: the ViewModel holds it, so there is nothing for the sheet to
     * hand back and no chance of submitting a form the state does not agree with.
     */
    data object Submit : AddTransactionAction()
}
