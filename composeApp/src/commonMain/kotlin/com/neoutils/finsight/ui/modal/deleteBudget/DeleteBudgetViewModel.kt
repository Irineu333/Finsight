package com.neoutils.finsight.ui.modal.deleteBudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteBudgetViewModel(
    private val budget: Budget,
    private val budgetRepository: IBudgetRepository,
    private val modalManager: ModalManager,
) : ViewModel() {

    fun deleteBudget() = viewModelScope.launch {
        budgetRepository.delete(budget)
        modalManager.dismissAll()
    }
}
