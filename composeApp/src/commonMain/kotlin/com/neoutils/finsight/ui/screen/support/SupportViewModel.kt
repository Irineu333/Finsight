package com.neoutils.finsight.ui.screen.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import com.neoutils.finsight.domain.repository.ISupportRepository
import com.neoutils.finsight.domain.usecase.CreateSupportIssueUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupportViewModel(
    supportRepository: ISupportRepository,
    private val createSupportIssueUseCase: CreateSupportIssueUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val _showActive = MutableStateFlow(true)

    val uiState = combine(
        supportRepository.observeIssues(),
        _showActive,
    ) { issues, showActive ->

        if (issues.isEmpty()) {
            return@combine SupportUiState.Empty(showActive = showActive)
        }

        SupportUiState.Content(
            issues = issues
                .filter { it.isActive == showActive }
                .sortedByDescending { it.updatedAt },
            showActive = showActive,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SupportUiState.Loading(
            showActive = _showActive.value
        ),
    )

    fun setFilter(showActive: Boolean) {
        _showActive.value = showActive
    }

    fun createIssue(draft: SupportIssueDraft) {
        viewModelScope.launch {
            createSupportIssueUseCase(draft)
            analytics.logEvent(
                name = "create_support_issue",
                params = mapOf("type" to draft.type.name.lowercase()),
            )
        }
    }
}
