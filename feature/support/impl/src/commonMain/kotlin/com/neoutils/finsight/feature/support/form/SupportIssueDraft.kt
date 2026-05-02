package com.neoutils.finsight.feature.support.form

import com.neoutils.finsight.feature.support.model.SupportIssue
data class SupportIssueDraft(
    val type: SupportIssue.Type,
    val title: String,
    val description: String,
)
