package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.analytics.putList
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType

class CreateBudget(params: Map<String, String>) : Event("create_budget", params) {
    constructor(limitType: LimitType, categories: List<Category>) : this(
        budgetParams(limitType, categories)
    )
}

class EditBudget(params: Map<String, String>) : Event("edit_budget", params) {
    constructor(limitType: LimitType, categories: List<Category>) : this(
        budgetParams(limitType, categories)
    )
}

object DeleteBudget : Event("delete_budget")

private fun budgetParams(limitType: LimitType, categories: List<Category>) = buildMap {
    put("type", limitType.name.lowercase())
    putList("categories", categories.map { it.name })
}
