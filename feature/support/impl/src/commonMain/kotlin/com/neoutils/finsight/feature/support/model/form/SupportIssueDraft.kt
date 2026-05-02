package com.neoutils.finsight.feature.support.model.form

import com.neoutils.finsight.feature.support.model.SupportIssue
data class SupportIssueDraft(
    val type: SupportIssue.Type,
    val title: String,
    val description: String,
)
