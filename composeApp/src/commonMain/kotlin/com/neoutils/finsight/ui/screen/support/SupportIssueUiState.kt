package com.neoutils.finsight.ui.screen.support

import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.SupportMessage

sealed class SupportIssueUiState {

    data object Loading : SupportIssueUiState()

    data class Content(
        val issue: SupportIssue,
        val messages: List<SupportMessage> = emptyList(),
        val replyText: String = "",
        val isSending: Boolean = false,
        val integrationPending: Boolean = false,
    ) : SupportIssueUiState() {
        val canSend: Boolean
            get() = replyText.isNotBlank() && !isSending
    }
}
