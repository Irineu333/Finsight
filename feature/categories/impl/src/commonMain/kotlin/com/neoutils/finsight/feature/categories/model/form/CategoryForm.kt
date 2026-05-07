@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.categories.model.form

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.core.ui.util.AppIcon
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class CategoryForm(
    val id: Long = 0,
    val name: String = "",
    val type: Category.Type = Category.Type.EXPENSE,
    val icon: AppIcon = AppIcon.CATEGORY,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
) {
    fun build(): Category {
        return Category(
            id = id,
            name = name,
            type = type,
            iconKey = icon.key,
            createdAt = createdAt,
        )
    }
}
