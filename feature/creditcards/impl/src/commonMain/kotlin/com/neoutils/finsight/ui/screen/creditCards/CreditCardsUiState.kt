package com.neoutils.finsight.ui.screen.creditCards

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.model.CreditCardUi
import kotlinx.datetime.LocalDate

sealed class CreditCardsUiState {

    data object Loading : CreditCardsUiState()

    data object Empty : CreditCardsUiState()

    data class Content(
        val creditCards: List<CreditCardUi>,
        val selectedCardIndex: Int,
        val transactions: Map<LocalDate, List<Transaction>>,
        val categories: List<Category>,
        val selectedCategory: Category?,
        val selectedType: TransactionType?,
        val showRecurringOnly: Boolean,
        val showInstallmentOnly: Boolean,
    ) : CreditCardsUiState()
}
