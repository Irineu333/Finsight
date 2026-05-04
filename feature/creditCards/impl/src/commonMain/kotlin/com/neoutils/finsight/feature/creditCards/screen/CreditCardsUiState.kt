package com.neoutils.finsight.feature.creditCards.screen

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.creditCards.model.CreditCardUi
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
        val showInstallmentOnly: Boolean,
    ) : CreditCardsUiState()
}
