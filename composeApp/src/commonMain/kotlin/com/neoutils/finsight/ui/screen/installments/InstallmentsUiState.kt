package com.neoutils.finsight.ui.screen.installments

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
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
        val installments: List<InstallmentWithOperationsUi>,
        val selectedInstallmentIndex: Int,
        val selectedCategory: Category?,
        val selectedType: Transaction.Type?,
        override val selectedFilter: InstallmentFilter,
        val categories: List<Category>,
    ) : InstallmentsUiState() {

        val selectedInstallment: InstallmentWithOperationsUi?
            get() = installments.getOrNull(selectedInstallmentIndex)

        val filteredOperations: List<Operation>
            get() {
                val operations = selectedInstallment?.operations.orEmpty()
                return operations.filter { operation ->
                    (selectedCategory == null || operation.category?.id == selectedCategory.id) &&
                            (selectedType == null || operation.type == selectedType)
                }
            }
    }
}

data class InstallmentWithOperationsUi(
    val installment: Installment,
    val operations: List<Operation>,
    val latestOperationDate: LocalDate,
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