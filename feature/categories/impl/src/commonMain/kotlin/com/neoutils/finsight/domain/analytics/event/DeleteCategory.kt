package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Category

class DeleteCategory(params: Map<String, String>) : Event("delete_category", params) {
    constructor(category: Category) : this(
        mapOf("name" to category.name, "type" to category.type.name.lowercase())
    )
}
