package com.neoutils.finsight.feature.recurring.mapper

import com.neoutils.finsight.core.database.entity.RecurringEntity
import com.neoutils.finsight.feature.recurring.model.Recurring

class RecurringMapper : IRecurringMapper {

    override fun toDomain(entity: RecurringEntity): Recurring = Recurring(
        id = entity.id,
        type = when (entity.type) {
            RecurringEntity.Type.EXPENSE -> Recurring.Type.EXPENSE
            RecurringEntity.Type.INCOME -> Recurring.Type.INCOME
        },
        amount = entity.amount,
        title = entity.title,
        dayOfMonth = entity.dayOfMonth,
        categoryId = entity.categoryId,
        accountId = entity.accountId,
        creditCardId = entity.creditCardId,
        createdAt = entity.createdAt,
        isActive = entity.isActive,
    )

    override fun toEntity(recurring: Recurring): RecurringEntity = RecurringEntity(
        id = recurring.id,
        type = when (recurring.type) {
            Recurring.Type.EXPENSE -> RecurringEntity.Type.EXPENSE
            Recurring.Type.INCOME -> RecurringEntity.Type.INCOME
        },
        amount = recurring.amount,
        title = recurring.title,
        dayOfMonth = recurring.dayOfMonth,
        categoryId = recurring.categoryId,
        accountId = recurring.accountId,
        creditCardId = recurring.creditCardId,
        createdAt = recurring.createdAt,
        isActive = recurring.isActive,
    )
}
