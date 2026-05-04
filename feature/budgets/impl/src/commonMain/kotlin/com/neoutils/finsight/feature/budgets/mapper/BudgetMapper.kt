package com.neoutils.finsight.feature.budgets.mapper

import com.neoutils.finsight.core.database.entity.BudgetEntity
import com.neoutils.finsight.feature.budgets.model.Budget
import com.neoutils.finsight.feature.budgets.model.LimitType

class BudgetMapper {
    fun toDomain(entity: BudgetEntity, categoryIds: List<Long>): Budget {
        return Budget(
            id = entity.id,
            title = entity.title,
            categoryIds = categoryIds,
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
            categoryId = domain.categoryIds.firstOrNull() ?: 0,
            iconCategoryId = domain.categoryIds.firstOrNull() ?: 0,
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
