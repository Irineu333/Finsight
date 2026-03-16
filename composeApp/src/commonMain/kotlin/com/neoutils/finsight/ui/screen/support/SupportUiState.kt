package com.neoutils.finsight.ui.screen.support

import com.neoutils.finsight.domain.model.SupportIssue

sealed class SupportUiState {

    data object Loading : SupportUiState()

    data class Content(
        val issues: List<SupportIssue>,
        val integrationPending: Boolean = false,
    ) : SupportUiState() {
        val waitingSupportCount: Int
            get() = issues.count { it.isWaitingSupportReply }
    }
}
