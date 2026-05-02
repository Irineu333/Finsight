package com.neoutils.finsight.feature.support.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.support.model.SupportIssue

class SendSupportReply(params: Map<String, String>) : Event("send_support_reply", params) {
    constructor(issue: SupportIssue) : this(mapOf("type" to issue.type.name.lowercase()))
}