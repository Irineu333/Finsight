package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.RecurringEntity
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType

class RecurringMapper {

    fun toDomain(
        entity: RecurringEntity,
        category: Category?,
        account: Account?,
        creditCard: CreditCard?,
    ): Recurring = Recurring(
        id = entity.id,
        type = when (entity.type) {
            RecurringEntity.Type.EXPENSE -> TransactionType.EXPENSE
            RecurringEntity.Type.INCOME -> TransactionType.INCOME
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
            TransactionType.EXPENSE -> RecurringEntity.Type.EXPENSE
            TransactionType.INCOME -> RecurringEntity.Type.INCOME
            TransactionType.ADJUSTMENT -> RecurringEntity.Type.EXPENSE
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
