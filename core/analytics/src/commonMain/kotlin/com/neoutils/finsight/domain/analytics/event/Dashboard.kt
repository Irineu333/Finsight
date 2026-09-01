package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.analytics.putList

object EnterDashboardEditMode : Event("enter_dashboard_edit_mode")

class SaveDashboardLayout(params: Map<String, String>) : Event("save_dashboard_layout", params) {
    constructor(components: List<String>) : this(
        buildMap { putList("components", components) }
    )
}
