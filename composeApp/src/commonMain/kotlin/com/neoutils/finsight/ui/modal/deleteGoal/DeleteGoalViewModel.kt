package com.neoutils.finsight.ui.modal.deleteGoal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Goal
import com.neoutils.finsight.domain.repository.IGoalRepository
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteGoalViewModel(
    private val goal: Goal,
    private val goalRepository: IGoalRepository,
    private val modalManager: ModalManager,
) : ViewModel() {

    fun deleteGoal() = viewModelScope.launch {
        goalRepository.delete(goal)
        modalManager.dismissAll()
    }
}
