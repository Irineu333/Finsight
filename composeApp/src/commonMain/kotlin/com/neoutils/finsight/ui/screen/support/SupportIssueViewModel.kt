package com.neoutils.finsight.ui.screen.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.ISupportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupportIssueViewModel(
    issueId: String,
    private val supportRepository: ISupportRepository,
) : ViewModel() {

    private val replyText = MutableStateFlow("")
    private val isSending = MutableStateFlow(false)

    val uiState = combine(
        supportRepository.observeIssueById(issueId),
        replyText,
        isSending,
    ) { issue, replyText, isSending ->
        SupportIssueUiState(
            issue = issue,
            replyText = replyText,
            isSending = isSending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SupportIssueUiState(),
    )

    fun onReplyTextChange(value: String) {
        replyText.value = value
    }

    fun sendReply() {
        val issue = uiState.value.issue ?: return
        val reply = uiState.value.replyText.trim()
        if (reply.isBlank() || uiState.value.isSending) return

        viewModelScope.launch {
            isSending.value = true
            supportRepository.addReply(
                issueId = issue.id,
                message = reply,
            )
            replyText.value = ""
            isSending.value = false
        }
    }
}
