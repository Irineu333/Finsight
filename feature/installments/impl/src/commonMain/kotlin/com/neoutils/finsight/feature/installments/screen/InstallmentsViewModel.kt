package com.neoutils.finsight.feature.installments.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.installments.mapper.InstallmentUiMapper
import com.neoutils.finsight.feature.installments.repository.IInstallmentRepository
import com.neoutils.finsight.feature.transactions.mapper.IOperationUiMapper
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class InstallmentsViewModel(
    installmentRepository: IInstallmentRepository,
    operationRepository: IOperationRepository,
    categoryRepository: ICategoryRepository,
    private val installmentUiMapper: InstallmentUiMapper,
    private val operationUiMapper: IOperationUiMapper,
) : ViewModel() {

    private val selectedInstallmentIndex = MutableStateFlow(0)
    private val selectedCategory = MutableStateFlow<Category?>(null)
    private val selectedType = MutableStateFlow<Transaction.Type?>(null)
    private val selectedFilter = MutableStateFlow(InstallmentFilter.ACTIVE)

    private val allInstallmentsUi = combine(
        installmentRepository.observeAllInstallments(),
        operationRepository.observeAllOperations(),
    ) { installments, operations ->
        val operationsWithInstallment = operations.filter { it.installment != null }
        val perspective = OperationPerspective.Account(accountId = 0L)
        val operationsUiByInstallmentId = operationUiMapper
            .toUi(operationsWithInstallment, perspective)
            .groupBy { checkNotNull(it.operation.installment).id }

        installments
            .mapNotNull { installment ->
                installmentUiMapper.toUi(
                    installment = installment,
                    operations = operationsUiByInstallmentId[installment.id].orEmpty(),
                )
            }
            .sortedWith(
                compareByDescending<InstallmentWithOperationsUi> {
                    it.latestOperationDate
                }.thenByDescending { it.installment.id }
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

        if (filtered.isEmpty()) {
            return@combine InstallmentsUiState.Empty(selectedFilter = filter)
        }

        val safeSelectedIndex = selectedIndex
            .coerceAtLeast(0)
            .coerceAtMost(filtered.size - 1)

        val selectedOperations = filtered.getOrNull(safeSelectedIndex)
            ?.operations.orEmpty()

        val categoryIds = selectedOperations.mapNotNull { it.operation.categoryId }.toSet()
        val categories = filtered
            .mapNotNull { it.category?.takeIf { c -> c.id in categoryIds } }
            .distinctBy { it.id }

        InstallmentsUiState.Content(
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
        initialValue = InstallmentsUiState.Loading(),
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
