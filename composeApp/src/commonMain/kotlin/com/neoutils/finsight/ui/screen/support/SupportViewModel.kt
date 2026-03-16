package com.neoutils.finsight.ui.screen.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import com.neoutils.finsight.domain.repository.ISupportRepository
import com.neoutils.finsight.domain.usecase.CreateSupportIssueUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupportViewModel(
    supportRepository: ISupportRepository,
    private val createSupportIssueUseCase: CreateSupportIssueUseCase,
) : ViewModel() {

    val uiState = supportRepository.observeIssues()
        .map { issues ->
            SupportUiState.Content(
                issues = issues.sortedByDescending { it.updatedAt },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SupportUiState.Loading,
        )

    fun createIssue(
        draft: SupportIssueDraft,
        onIssueCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            createSupportIssueUseCase(draft).fold(
                ifLeft = {},
                ifRight = { issue ->
                    onIssueCreated(issue.id)
                },
            )
        }
    }
}
