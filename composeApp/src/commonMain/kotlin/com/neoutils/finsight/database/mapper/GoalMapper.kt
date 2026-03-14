package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.GoalEntity
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Goal

class GoalMapper {
    fun toDomain(entity: GoalEntity, categories: List<Category>): Goal {
        return Goal(
            id = entity.id,
            title = entity.title,
            categories = categories,
            iconKey = entity.iconKey,
            amount = entity.amount,
            createdAt = entity.createdAt,
        )
    }

    fun toEntity(domain: Goal): GoalEntity {
        return GoalEntity(
            id = domain.id,
            categoryId = domain.categories.firstOrNull()?.id ?: 0,
            iconCategoryId = domain.categories.firstOrNull()?.id ?: 0,
            iconKey = domain.iconKey,
            title = domain.title,
            amount = domain.amount,
            period = "MONTHLY",
            createdAt = domain.createdAt,
        )
    }
}
