package com.neoutils.finsight.feature.support.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.support.form.SupportIssueDraft

class CreateSupportIssue(params: Map<String, String>) : Event("create_support_issue", params) {
    constructor(draft: SupportIssueDraft) : this(mapOf("type" to draft.type.name.lowercase()))
}
