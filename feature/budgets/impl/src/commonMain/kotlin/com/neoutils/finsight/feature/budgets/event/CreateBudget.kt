package com.neoutils.finsight.feature.budgets.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.budgets.model.LimitType

class CreateBudget(params: Map<String, String>) : Event("create_budget", params) {
    constructor(limitType: LimitType, categories: List<Category>) : this(
        buildMap {
            put("type", limitType.name.lowercase())
            put("categories", categories.joinToString(",") { it.name })
        }
    )
}
