package com.neoutils.finance.ui.screen.installments

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Installment
import com.neoutils.finance.domain.model.Operation
import com.neoutils.finance.domain.model.Transaction
import kotlinx.datetime.LocalDate

data class InstallmentsUiState(
    val installments: List<InstallmentWithOperationsUi> = emptyList(),
    val selectedInstallmentIndex: Int = 0,
    val selectedCategory: Category? = null,
    val selectedType: Transaction.Type? = null,
    val categories: List<Category> = emptyList(),
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
)
