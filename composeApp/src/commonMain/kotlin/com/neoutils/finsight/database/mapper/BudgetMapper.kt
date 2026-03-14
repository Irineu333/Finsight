package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.BudgetEntity
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType

class BudgetMapper {
    fun toDomain(entity: BudgetEntity, categories: List<Category>): Budget {
        return Budget(
            id = entity.id,
            title = entity.title,
            categories = categories,
            iconKey = entity.iconKey,
            amount = entity.amount,
            limitType = runCatching { LimitType.valueOf(entity.limitType) }.getOrDefault(LimitType.FIXED),
            percentage = entity.percentage,
            recurringId = entity.recurringId,
            createdAt = entity.createdAt,
        )
    }

    fun toEntity(domain: Budget): BudgetEntity {
        return BudgetEntity(
            id = domain.id,
            categoryId = domain.categories.firstOrNull()?.id ?: 0,
            iconCategoryId = domain.categories.firstOrNull()?.id ?: 0,
            iconKey = domain.iconKey,
            title = domain.title,
            amount = domain.amount,
            period = "MONTHLY",
            limitType = domain.limitType.name,
            percentage = domain.percentage,
            recurringId = domain.recurringId,
            createdAt = domain.createdAt,
        )
    }
}
