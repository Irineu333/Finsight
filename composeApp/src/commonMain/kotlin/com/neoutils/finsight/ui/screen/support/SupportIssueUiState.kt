package com.neoutils.finsight.ui.screen.support

import com.neoutils.finsight.domain.model.SupportIssue

data class SupportIssueUiState(
    val issue: SupportIssue? = null,
    val replyText: String = "",
    val isSending: Boolean = false,
    val integrationPending: Boolean = true,
) {
    val canSend: Boolean
        get() = issue != null && replyText.isNotBlank() && !isSending
}
