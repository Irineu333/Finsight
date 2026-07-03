package com.neoutils.finsight.feature.support.api

import kotlinx.serialization.Serializable

@Serializable
data object SupportRoute

@Serializable
data class SupportIssueRoute(val issueId: String)
