package com.neoutils.finsight.ui.screen.installments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class InstallmentsViewModel(
    installmentRepository: IInstallmentRepository,
    operationRepository: IOperationRepository,
) : ViewModel() {

    private val selectedInstallmentIndex = MutableStateFlow(0)
    private val selectedCategory = MutableStateFlow<Category?>(null)
    private val selectedType = MutableStateFlow<Transaction.Type?>(null)
    private val selectedFilter = MutableStateFlow(InstallmentFilter.ACTIVE)

    private val allInstallmentsUi = combine(
        installmentRepository.observeAllInstallments(),
        operationRepository.observeAllOperations(),
    ) { installments, operations ->
        val operationsByInstallmentId = operations
            .filter { it.installment != null }
            .groupBy { checkNotNull(it.installment).id }

        installments
            .mapNotNull { installment ->
                val installmentOperations = operationsByInstallmentId[installment.id]
                    .orEmpty()
                    .sortedBy { it.installment?.number ?: Int.MAX_VALUE }

                if (installmentOperations.isEmpty()) {
                    null
                } else {
                    val firstOperation = installmentOperations.first()
                    val openOperation = installmentOperations.firstOrNull {
                        it.targetInvoice?.status?.isOpen == true
                    }
                    val currentNumber = openOperation?.installment?.number
                        ?: installmentOperations.last().installment?.number
                        ?: installment.count
                    val installmentAmount = installment.totalAmount / installment.count
                    val paidCount = installmentOperations.count {
                        it.targetInvoice?.status?.isPaid == true
                    }
                    val isActive = paidCount < installment.count

                    InstallmentWithOperationsUi(
                        installment = installment,
                        operations = installmentOperations,
                        latestOperationDate = installmentOperations.maxOf { it.date },
                        title = firstOperation.label,
                        categoryName = firstOperation.category?.name?.uppercase(),
                        category = firstOperation.category,
                        isActive = isActive,
                        currentNumber = currentNumber,
                        installmentAmount = installmentAmount,
                        remainingAmount = (installment.count - paidCount) * installmentAmount,
                        progress = currentNumber.toFloat() / installment.count,
                        isDeletable = installmentOperations.all {
                            it.targetInvoice?.status?.isEditable != false
                        },
                    )
                }
            }
            .sortedWith(
                compareByDescending<InstallmentWithOperationsUi> { it.latestOperationDate }
                    .thenByDescending { it.installment.id }
            )
    }

    val uiState = combine(
        allInstallmentsUi,
        selectedInstallmentIndex,
        selectedCategory,
        selectedType,
        selectedFilter,
    ) { installmentsAll, selectedIndex, category, type, filter ->
        val filtered = when (filter) {
            InstallmentFilter.ACTIVE -> installmentsAll.filter { it.isActive }
            InstallmentFilter.COMPLETED -> installmentsAll.filter { !it.isActive }
            InstallmentFilter.ALL -> installmentsAll
        }

        val safeSelectedIndex = selectedIndex
            .coerceAtLeast(0)
            .coerceAtMost((filtered.size - 1).coerceAtLeast(0))

        val selectedOperations = filtered.getOrNull(safeSelectedIndex)
            ?.operations.orEmpty()

        val categories = selectedOperations
            .mapNotNull { it.category }
            .distinctBy { it.id }

        InstallmentsUiState(
            installments = filtered,
            selectedInstallmentIndex = safeSelectedIndex,
            selectedCategory = category,
            selectedType = type,
            selectedFilter = filter,
            categories = categories,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InstallmentsUiState(),
    )

    fun onAction(action: InstallmentsAction) {
        when (action) {
            is InstallmentsAction.SelectInstallment -> {
                selectedInstallmentIndex.update { action.index.coerceAtLeast(0) }
                selectedCategory.value = null
                selectedType.value = null
            }
            is InstallmentsAction.SelectCategory -> {
                selectedCategory.value = action.category
            }
            is InstallmentsAction.SelectType -> {
                selectedType.value = action.type
            }
            is InstallmentsAction.SelectFilter -> {
                selectedFilter.value = action.filter
                selectedInstallmentIndex.value = 0
                selectedCategory.value = null
                selectedType.value = null
            }
        }
    }
}
