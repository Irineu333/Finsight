package com.neoutils.finsight.feature.categories.event

import com.neoutils.finsight.core.analytics.Event
import com.neoutils.finsight.feature.categories.model.Category

class EditCategory(params: Map<String, String>) : Event("edit_category", params) {
    constructor(name: String, type: Category.Type) : this(
        mapOf("name" to name, "type" to type.name.lowercase())
    )
}
