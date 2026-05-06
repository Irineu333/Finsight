package com.neoutils.finsight.feature.installments.screen

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.installments.model.Installment
import com.neoutils.finsight.feature.transactions.model.OperationUi
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.datetime.LocalDate

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

        val filteredOperations: List<OperationUi>
            get() {
                val operations = selectedInstallment?.operations.orEmpty()
                return operations.filter { operationUi ->
                    val operation = operationUi.operation
                    (selectedCategory == null || operation.categoryId == selectedCategory.id) &&
                            (selectedType == null || operation.type == selectedType)
                }
            }
    }
}

data class InstallmentWithOperationsUi(
    val installment: Installment,
    val operations: List<OperationUi>,
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
