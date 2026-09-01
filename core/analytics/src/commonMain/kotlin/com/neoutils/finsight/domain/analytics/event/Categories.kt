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

/** The row is gone. Retiring one that must be preserved is [ArchiveCategory] instead. */
class DeleteCategory(params: Map<String, String>) : Event("delete_category", params) {
    constructor(category: Category) : this(category.asParams())
}

/** Retired but kept, and reversible by [UnarchiveCategory] — not a deletion. */
class ArchiveCategory(params: Map<String, String>) : Event("archive_category", params) {
    constructor(category: Category) : this(category.asParams())
}

class UnarchiveCategory(params: Map<String, String>) : Event("unarchive_category", params) {
    constructor(category: Category) : this(category.asParams())
}

private fun Category.asParams() = mapOf("name" to name, "type" to type.name.lowercase())
