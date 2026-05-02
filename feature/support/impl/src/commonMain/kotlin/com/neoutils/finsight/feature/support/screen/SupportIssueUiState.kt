package com.neoutils.finsight.feature.support.screen

import com.neoutils.finsight.feature.support.model.SupportIssue
import com.neoutils.finsight.feature.support.model.SupportMessage
sealed class SupportIssueUiState {

    data object Loading : SupportIssueUiState()

    data class Content(
        val issue: SupportIssue,
        val messages: List<SupportMessage> = emptyList(),
        val replyText: String = "",
    ) : SupportIssueUiState() {
        val canSend: Boolean
            get() = replyText.isNotBlank()
    }
}
