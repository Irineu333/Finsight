package com.neoutils.finsight.ui.screen.creditCards

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.model.InvoiceUi
import kotlinx.datetime.LocalDate

data class CreditCardsUiState(
    val creditCards: List<CreditCardUi> = emptyList(),
    val selectedCardIndex: Int = 0,
    val operations: Map<LocalDate, List<Operation>> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val selectedType: Transaction.Type? = null,
    val recurring: List<Recurring> = emptyList(),
    val selectedRecurring: Recurring? = null,
)

data class CreditCardUi(
    val creditCard: CreditCard,
    val invoiceUi: InvoiceUi?,
)
