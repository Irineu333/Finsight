package com.neoutils.finsight.ui.screen.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.SendSupportReply
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.extension.interceptAbsence
import com.neoutils.finsight.feature.support.api.ISupportRepository
import com.neoutils.finsight.domain.usecase.AddSupportReplyUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupportIssueViewModel(
    issueId: String,
    supportRepository: ISupportRepository,
    private val addSupportReplyUseCase: AddSupportReplyUseCase,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val replyText = MutableStateFlow("")

    private val _events = Channel<SupportIssueEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val issueFlow = supportRepository
        .observeIssueById(issueId)
        // A null issue is terminal (never existed / deleted / parse failure), never a transient
        // loading state — leave the screen instead of hanging on the spinner. Repeats are collapsed,
        // so IssueDeleted fires once. Only an invalid id / load failure is worth reporting.
        .interceptAbsence(
            onMissing = {
                crashlytics.recordException(DetailNotFoundException("SupportIssue", issueId))
                _events.send(SupportIssueEvent.IssueDeleted)
            },
            onDisappeared = { _events.send(SupportIssueEvent.IssueDeleted) },
        )

    val uiState = combine(
        issueFlow,
        supportRepository.observeMessages(issueId),
        replyText,
    ) { issue, messages, replyText ->
        if (issue == null) {
            SupportIssueUiState.Loading
        } else {
            SupportIssueUiState.Content(
                issue = issue,
                messages = messages,
                replyText = replyText,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SupportIssueUiState.Loading,
    )

    fun onReplyTextChange(value: String) {
        replyText.value = value
    }

    fun sendReply() {
        val content = uiState.value as? SupportIssueUiState.Content ?: return
        val reply = content.replyText.trim()
        if (reply.isBlank()) return

        replyText.value = ""

        viewModelScope.launch {
            addSupportReplyUseCase(issueId = content.issue.id, message = reply)
                .onLeft {
                    crashlytics.recordException(it)
                }
                .onRight {
                    analytics.logEvent(SendSupportReply(content.issue))
                }
        }
    }
}
