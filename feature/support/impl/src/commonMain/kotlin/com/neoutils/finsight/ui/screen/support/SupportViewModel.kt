package com.neoutils.finsight.ui.screen.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateSupportIssue
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import com.neoutils.finsight.feature.support.api.ISupportRepository
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
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _showActive = MutableStateFlow(true)

    val uiState = combine(
        supportRepository.observeIssues(),
        _showActive,
    ) { issues, showActive ->

        val scoped = issues.filter { it.isActive == showActive }

        // Decided on the filtered list: emptiness is a fact about the scope on screen,
        // not about the archive behind it. Judged before the filter, a scope with
        // nothing in it drew a Content of no rows — a dead screen with no empty state.
        if (scoped.isEmpty()) {
            return@combine SupportUiState.Empty(showActive = showActive)
        }

        SupportUiState.Content(
            issues = scoped.sortedByDescending { it.updatedAt },
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
                .onLeft {
                    crashlytics.recordException(it)
                }
                .onRight {
                    analytics.logEvent(CreateSupportIssue(draft))
                }
        }
    }
}
