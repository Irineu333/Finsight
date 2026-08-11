package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event

class GenerateReport(params: Map<String, String>) : Event("generate_report", params) {
    constructor(target: String, sections: String) : this(
        mapOf("target" to target, "sections" to sections)
    )
}

object ShareReport : Event("share_report")

object PrintReport : Event("print_report")
