package com.neoutils.finsight.feature.support.screen

import com.neoutils.finsight.feature.support.model.SupportIssue
sealed class SupportUiState {

    abstract val showActive: Boolean

    data class Loading(
        override val showActive: Boolean = true,
    ) : SupportUiState()

    data class Content(
        val issues: List<SupportIssue>,
        override val showActive: Boolean = true,
    ) : SupportUiState() {
        val waitingSupportCount = issues.count { it.isWaitingSupportReply }
    }

    data class Empty(
        override val showActive: Boolean = true,
    ) : SupportUiState()
}
