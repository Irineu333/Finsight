package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType

class CreateBudget(params: Map<String, String>) : Event("create_budget", params) {
    constructor(limitType: LimitType, categories: List<Category>) : this(
        buildMap {
            put("type", limitType.name.lowercase())
            put("categories", categories.joinToString(",") { it.name })
        }
    )
}

class EditBudget(params: Map<String, String>) : Event("edit_budget", params) {
    constructor(limitType: LimitType, categories: List<Category>) : this(
        buildMap {
            put("type", limitType.name.lowercase())
            put("categories", categories.joinToString(",") { it.name })
        }
    )
}

object DeleteBudget : Event("delete_budget")
