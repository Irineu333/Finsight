package com.neoutils.finsight.ui.screen.support

import kotlinx.serialization.Serializable

@Serializable
data object SupportListRoute

@Serializable
data class SupportIssueRoute(val issueId: String)
