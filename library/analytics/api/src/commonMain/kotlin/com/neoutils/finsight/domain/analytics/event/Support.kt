package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.form.SupportIssueDraft

class CreateSupportIssue(params: Map<String, String>) : Event("create_support_issue", params) {
    constructor(draft: SupportIssueDraft) : this(mapOf("type" to draft.type.name.lowercase()))
}

class SendSupportReply(params: Map<String, String>) : Event("send_support_reply", params) {
    constructor(issue: SupportIssue) : this(mapOf("type" to issue.type.name.lowercase()))
}
