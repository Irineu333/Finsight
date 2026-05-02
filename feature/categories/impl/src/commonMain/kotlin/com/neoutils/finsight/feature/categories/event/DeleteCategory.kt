package com.neoutils.finsight.feature.categories.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.core.domain.model.Category

class DeleteCategory(params: Map<String, String>) : Event("delete_category", params) {
    constructor(category: Category) : this(
        mapOf("name" to category.name, "type" to category.type.name.lowercase())
    )
}
