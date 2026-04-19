package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.RecurringEntity
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring
class RecurringMapper {

    fun toDomain(
        entity: RecurringEntity,
        category: Category?,
        account: Account?,
        creditCard: CreditCard?,
    ): Recurring = Recurring(
        id = entity.id,
        type = when (entity.type) {
            RecurringEntity.Type.EXPENSE -> Recurring.Type.EXPENSE
            RecurringEntity.Type.INCOME -> Recurring.Type.INCOME
        },
        amount = entity.amount,
        title = entity.title,
        dayOfMonth = entity.dayOfMonth,
        category = category,
        account = account,
        creditCard = creditCard,
        createdAt = entity.createdAt,
        isActive = entity.isActive,
    )

    fun toEntity(recurring: Recurring): RecurringEntity = RecurringEntity(
        id = recurring.id,
        type = when (recurring.type) {
            Recurring.Type.EXPENSE -> RecurringEntity.Type.EXPENSE
            Recurring.Type.INCOME -> RecurringEntity.Type.INCOME
        },
        amount = recurring.amount,
        title = recurring.title,
        dayOfMonth = recurring.dayOfMonth,
        categoryId = recurring.category?.id,
        accountId = recurring.account?.id,
        creditCardId = recurring.creditCard?.id,
        createdAt = recurring.createdAt,
        isActive = recurring.isActive,
    )
}
