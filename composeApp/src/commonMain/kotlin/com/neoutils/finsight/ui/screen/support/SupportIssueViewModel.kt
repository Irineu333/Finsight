package com.neoutils.finsight.ui.screen.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.ISupportRepository
import com.neoutils.finsight.domain.usecase.AddSupportReplyUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupportIssueViewModel(
    issueId: String,
    supportRepository: ISupportRepository,
    private val addSupportReplyUseCase: AddSupportReplyUseCase,
) : ViewModel() {

    private val replyText = MutableStateFlow("")
    private val isSending = MutableStateFlow(false)

    val uiState = combine(
        supportRepository.observeIssueById(issueId),
        supportRepository.observeMessages(issueId),
        replyText,
        isSending,
    ) { issue, messages, replyText, isSending ->
        if (issue == null) {
            SupportIssueUiState.Loading
        } else {
            SupportIssueUiState.Content(
                issue = issue,
                messages = messages,
                replyText = replyText,
                isSending = isSending,
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
        if (reply.isBlank() || content.isSending) return

        viewModelScope.launch {
            isSending.value = true
            addSupportReplyUseCase(issueId = content.issue.id, message = reply).fold(
                ifLeft = {},
                ifRight = { replyText.value = "" },
            )
            isSending.value = false
        }
    }
}
