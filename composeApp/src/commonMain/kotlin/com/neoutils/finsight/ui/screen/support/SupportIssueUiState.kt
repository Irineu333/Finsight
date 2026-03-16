package com.neoutils.finsight.ui.screen.support

import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.SupportMessage

data class SupportIssueUiState(
    val issue: SupportIssue? = null,
    val messages: List<SupportMessage> = emptyList(),
    val replyText: String = "",
    val isSending: Boolean = false,
    val integrationPending: Boolean = false,
) {
    val canSend: Boolean
        get() = issue != null && replyText.isNotBlank() && !isSending
}
