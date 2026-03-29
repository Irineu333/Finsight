package com.neoutils.finsight.domain.model

data class DashboardComponentPreference(
    val key: String,
    val position: Int,
    val config: Map<String, String> = emptyMap(),
)