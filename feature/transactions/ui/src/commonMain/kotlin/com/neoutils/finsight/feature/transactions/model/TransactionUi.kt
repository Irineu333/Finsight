package com.neoutils.finsight.feature.transactions.model

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.transactions.model.Transaction

data class TransactionUi(
    val transaction: Transaction,
    val account: Account? = null,
    val category: Category? = null,
    val creditCard: CreditCard? = null,
    val invoice: com.neoutils.finsight.feature.creditCards.model.InvoiceUi? = null,
) {
    val id: Long get() = transaction.id
    val type: Transaction.Type get() = transaction.type
    val amount: Double get() = transaction.amount
    val date = transaction.date
    val title = transaction.title
    val target = transaction.target
    val isInvoicePayment: Boolean get() = transaction.isInvoicePayment
}
