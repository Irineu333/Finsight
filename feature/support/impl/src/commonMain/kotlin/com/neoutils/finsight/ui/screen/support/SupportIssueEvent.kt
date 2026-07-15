package com.neoutils.finsight.ui.screen.support

sealed interface SupportIssueEvent {
    data object IssueDeleted : SupportIssueEvent
}
