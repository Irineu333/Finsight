package com.neoutils.finsight.ui.screen.support

import com.neoutils.finsight.domain.model.SupportIssue

data class SupportUiState(
    val issues: List<SupportIssue> = emptyList(),
    val integrationPending: Boolean = true,
) {
    val waitingSupportCount: Int
        get() = issues.count { it.isWaitingSupportReply }
}
