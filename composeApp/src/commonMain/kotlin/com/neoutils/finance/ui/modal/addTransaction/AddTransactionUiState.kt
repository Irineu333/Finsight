@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.addTransaction

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toLastDayOfMonth
import com.neoutils.finance.extension.toLocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class AddTransactionUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val currentInvoice: Invoice? = null
) {
    val targets = if (creditCards.isEmpty()) {
        listOf(Transaction.Target.ACCOUNT)
    } else {
        listOf(Transaction.Target.ACCOUNT, Transaction.Target.CREDIT_CARD)
    }

    val minDate = currentInvoice?.openingMonth?.firstDay

    val maxDate = currentInvoice?.closingMonth?.lastDay?.coerceAtMost(
        maximumValue = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
}