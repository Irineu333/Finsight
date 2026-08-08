package com.neoutils.finsight.ui.screen.creditCards

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.model.CreditCardUi
import com.neoutils.finsight.ui.model.TransactionUi
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
        // Each card's limit, denominated by the card's own account (design D17). It
        // cannot ride on `CreditCardUi.limit`, which is a bare `Double` in `:core:ui`
        // and shared with the dashboard. Aligned by index with [creditCards].
        val cardLimits: List<DisplayAmount>,
        val selectedCardIndex: Int,
        val listState: ListState,
        val categories: List<Category>,
        val selectedCategory: Category?,
        val selectedType: TransactionType?,
        val showRecurringOnly: Boolean,
        val showInstallmentOnly: Boolean,
    ) : CreditCardsUiState()

    /**
     * What stands where the list goes. The transactions live *inside* [ListState.Content]
     * rather than beside it, so there is no empty map awaiting interpretation.
     *
     * There is no loading case here — [CreditCardsUiState] already has one, and [Content]
     * only exists after the first read. Nor is [Empty] one of these: that one is about
     * having no card at all, and takes the whole screen, since without a card there is no
     * pager and no chips to preserve.
     */
    sealed interface ListState {

        /** The selected card's current invoice has no transaction at all. */
        data object EmptyInvoice : ListState

        /**
         * The invoice has transactions; none survives the active filters.
         * [canClearFilters] is false when every filter is already neutral.
         */
        data class EmptyScope(val canClearFilters: Boolean) : ListState

        data class Content(
            val transactions: Map<LocalDate, List<TransactionUi>>,
        ) : ListState
    }
}
