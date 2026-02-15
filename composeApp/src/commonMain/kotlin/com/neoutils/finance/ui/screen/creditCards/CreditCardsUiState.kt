package com.neoutils.finance.ui.screen.creditCards

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Operation
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.ui.model.InvoiceUi
import kotlinx.datetime.LocalDate

data class CreditCardsUiState(
    val creditCards: List<CreditCardUi> = emptyList(),
    val selectedCardIndex: Int = 0,
    val operations: Map<LocalDate, List<Operation>> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val selectedType: Transaction.Type? = null,
)

data class CreditCardUi(
    val creditCard: CreditCard,
    val invoiceUi: InvoiceUi?,
)
