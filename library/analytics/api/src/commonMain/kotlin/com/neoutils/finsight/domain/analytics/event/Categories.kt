package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Category

class CreateCategory(params: Map<String, String>) : Event("create_category", params) {
    constructor(name: String, type: Category.Type) : this(
        mapOf("name" to name, "type" to type.name.lowercase())
    )
}

class EditCategory(params: Map<String, String>) : Event("edit_category", params) {
    constructor(name: String, type: Category.Type) : this(
        mapOf("name" to name, "type" to type.name.lowercase())
    )
}

class DeleteCategory(params: Map<String, String>) : Event("delete_category", params) {
    constructor(category: Category) : this(
        mapOf("name" to category.name, "type" to category.type.name.lowercase())
    )
}
