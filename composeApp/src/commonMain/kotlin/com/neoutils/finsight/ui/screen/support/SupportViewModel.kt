package com.neoutils.finsight.ui.screen.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.ISupportRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SupportViewModel(
    supportRepository: ISupportRepository,
) : ViewModel() {

    val uiState = supportRepository.observeIssues()
        .map { issues ->
            SupportUiState(
                issues = issues.sortedByDescending { it.updatedAt },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SupportUiState(),
        )
}
