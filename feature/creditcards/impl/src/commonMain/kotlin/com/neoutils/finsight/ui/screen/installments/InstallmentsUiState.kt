package com.neoutils.finsight.ui.screen.installments

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.TransactionUi
import kotlinx.datetime.LocalDate

enum class InstallmentFilter {
    ACTIVE, COMPLETED, ALL
}

sealed class InstallmentsUiState {

    abstract val selectedFilter: InstallmentFilter

    data class Loading(
        override val selectedFilter: InstallmentFilter = InstallmentFilter.ACTIVE,
    ) : InstallmentsUiState()

    data class Empty(
        override val selectedFilter: InstallmentFilter,
    ) : InstallmentsUiState()

    data class Content(
        val installments: List<InstallmentUi>,
        val selectedInstallmentIndex: Int,
        // Already filtered and mapped by the ViewModel — the state holds no
        // transaction graph and derives nothing on read (`presentation-mapping`).
        val transactions: List<InstallmentTransactionUi>,
        // The domain of the selected installment, kept at state level (as
        // `AccountsUiState.domainAccounts` does) so the delete modal can be opened
        // while the display models above stay free of domain.
        val selectedDomainInstallment: Installment?,
        val selectedDomainTransactions: List<Transaction>,
        val selectedCategory: Category?,
        val selectedType: TransactionType?,
        override val selectedFilter: InstallmentFilter,
        val categories: List<Category>,
    ) : InstallmentsUiState() {

        val selectedInstallment: InstallmentUi?
            get() = installments.getOrNull(selectedInstallmentIndex)
    }
}

/**
 * A flat, display-ready view of an installment for the pager card. Carries no
 * domain graph — only resolved values and the installment id; the category is
 * reduced to what drawing it needs (icon, name, and the two facts
 * `categoryDisplayColor` depends on).
 */
data class InstallmentUi(
    val installmentId: Long,
    val latestTransactionDate: LocalDate,
    val title: String,
    val categoryName: String?,
    val categoryIcon: CategoryLazyIcon?,
    val categoryType: Category.Type?,
    val isCategoryArchived: Boolean,
    val isActive: Boolean,
    val currentNumber: Int,
    val totalCount: Int,
    val totalAmount: Double,
    val installmentAmount: Double,
    val remainingAmount: Double,
    val progress: Float,
    val isDeletable: Boolean,
)

/**
 * A transaction row of the selected installment. [isSettled] is the flat form of
 * "its invoice is already settled", which this list draws struck through.
 */
data class InstallmentTransactionUi(
    val transaction: TransactionUi,
    val isSettled: Boolean,
)
