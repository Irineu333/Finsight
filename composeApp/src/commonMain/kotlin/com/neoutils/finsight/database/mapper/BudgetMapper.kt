package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.BudgetEntity
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category

class BudgetMapper {
    fun toDomain(entity: BudgetEntity, categories: List<Category>): Budget {
        return Budget(
            id = entity.id,
            title = entity.title,
            categories = categories,
            iconCategoryId = entity.iconCategoryId,
            amount = entity.amount,
            createdAt = entity.createdAt,
        )
    }

    fun toEntity(domain: Budget): BudgetEntity {
        return BudgetEntity(
            id = domain.id,
            categoryId = domain.categories.firstOrNull()?.id ?: 0,
            iconCategoryId = domain.iconCategoryId,
            title = domain.title,
            amount = domain.amount,
            period = "MONTHLY",
            createdAt = domain.createdAt,
        )
    }
}
