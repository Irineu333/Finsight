package com.neoutils.finsight.feature.support.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.support.model.SupportIssue
import com.neoutils.finsight.feature.support.model.form.SupportIssueDraft
class CreateSupportIssue(params: Map<String, String>) : Event("create_support_issue", params) {
    constructor(draft: SupportIssueDraft) : this(mapOf("type" to draft.type.name.lowercase()))
}

class SendSupportReply(params: Map<String, String>) : Event("send_support_reply", params) {
    constructor(issue: SupportIssue) : this(mapOf("type" to issue.type.name.lowercase()))
}
