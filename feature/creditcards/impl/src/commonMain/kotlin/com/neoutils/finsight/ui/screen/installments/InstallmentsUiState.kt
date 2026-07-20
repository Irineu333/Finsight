package com.neoutils.finsight.ui.screen.installments

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.domain.model.TransactionType
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
        val installments: List<InstallmentWithTransactionsUi>,
        val selectedInstallmentIndex: Int,
        val selectedCategory: Category?,
        val selectedType: TransactionType?,
        override val selectedFilter: InstallmentFilter,
        val categories: List<Category>,
    ) : InstallmentsUiState() {

        val selectedInstallment: InstallmentWithTransactionsUi?
            get() = installments.getOrNull(selectedInstallmentIndex)

        val filteredTransactions: List<Transaction>
            get() {
                val transactions = selectedInstallment?.transactions.orEmpty()
                return transactions.filter { transaction ->
                    (selectedCategory == null || transaction.category?.id == selectedCategory.id) &&
                            (selectedType == null || transaction.primaryEntry?.let { deriveTransactionType(it.amount, transaction.entries) } == selectedType)
                }
            }
    }
}

data class InstallmentWithTransactionsUi(
    val installment: Installment,
    val transactions: List<Transaction>,
    val latestTransactionDate: LocalDate,
    val title: String,
    val categoryName: String?,
    val category: Category?,
    val isActive: Boolean,
    val currentNumber: Int,
    val installmentAmount: Double,
    val remainingAmount: Double,
    val progress: Float,
    val isDeletable: Boolean,
)