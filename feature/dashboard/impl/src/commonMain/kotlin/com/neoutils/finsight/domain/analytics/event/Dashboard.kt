package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.core.analytics.Event

object EnterDashboardEditMode : Event("enter_dashboard_edit_mode")

class SaveDashboardLayout(params: Map<String, String>) : Event("save_dashboard_layout", params) {
    constructor(components: String) : this(mapOf("components" to components))
}
