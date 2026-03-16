package com.neoutils.finsight.domain.model.form

import com.neoutils.finsight.domain.model.SupportIssue

data class SupportIssueDraft(
    val type: SupportIssue.Type,
    val title: String,
    val description: String,
)
