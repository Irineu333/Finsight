package com.neoutils.finsight.ui.screen.creditCards

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.model.InvoiceUi
import kotlinx.datetime.LocalDate

sealed class CreditCardsUiState {

    data object Loading : CreditCardsUiState()

    data object Empty : CreditCardsUiState()

    data class Content(
        val creditCards: List<CreditCardUi>,
        val selectedCardIndex: Int,
        val operations: Map<LocalDate, List<Operation>>,
        val categories: List<Category>,
        val selectedCategory: Category?,
        val selectedType: Transaction.Type?,
        val showRecurringOnly: Boolean,
    ) : CreditCardsUiState()
}

data class CreditCardUi(
    val creditCard: CreditCard,
    val invoiceUi: InvoiceUi?,
)