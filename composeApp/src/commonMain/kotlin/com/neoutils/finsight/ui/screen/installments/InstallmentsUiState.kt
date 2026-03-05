package com.neoutils.finsight.ui.screen.installments

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate

enum class InstallmentFilter {
    ACTIVE, COMPLETED, ALL
}

data class InstallmentsUiState(
    val installments: List<InstallmentWithOperationsUi> = emptyList(),
    val selectedInstallmentIndex: Int = 0,
    val selectedCategory: Category? = null,
    val selectedType: Transaction.Type? = null,
    val selectedFilter: InstallmentFilter = InstallmentFilter.ACTIVE,
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
) {
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
