package com.neoutils.finsight.ui.screen.creditCards

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.model.CreditCardUi
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import kotlinx.datetime.LocalDate

sealed class CreditCardsUiState {

    data object Loading : CreditCardsUiState()

    data object Empty : CreditCardsUiState()

    data class Content(
        val creditCards: List<CreditCardUi>,
        // Domain kept at the screen level (like the transactions/categories below, and
        // AccountsUiState.domainAccounts) so the flat CreditCardUi carries no graph while
        // the screen can still open the domain-taking card/invoice modals. Aligned by
        // index with [creditCards].
        val domainCards: List<CreditCard>,
        val domainInvoices: List<Invoice?>,
        val selectedCardIndex: Int,
        val transactions: Map<LocalDate, List<Transaction>>,
        val categories: List<Category>,
        val selectedCategory: Category?,
        val selectedType: TransactionType?,
        val showRecurringOnly: Boolean,
        val showInstallmentOnly: Boolean,
        val facadeLookup: TransactionFacadeLookup = TransactionFacadeLookup.EMPTY,
    ) : CreditCardsUiState()
}
