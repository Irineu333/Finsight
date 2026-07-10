package com.neoutils.finsight.ui.screen.support

import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

@Serializable
data object SupportListRoute : NavRoute

@Serializable
data class SupportIssueRoute(val issueId: String) : NavRoute
