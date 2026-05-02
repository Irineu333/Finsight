package com.neoutils.finsight.feature.dashboard.event

import com.neoutils.finsight.core.analytics.Event

class SaveDashboardLayout(params: Map<String, String>) : Event("save_dashboard_layout", params) {
    constructor(components: String) : this(mapOf("components" to components))
}