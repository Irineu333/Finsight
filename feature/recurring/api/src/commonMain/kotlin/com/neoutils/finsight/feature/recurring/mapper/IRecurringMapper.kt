package com.neoutils.finsight.feature.recurring.mapper

import com.neoutils.finsight.core.database.entity.RecurringEntity
import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.feature.recurring.model.Recurring

interface IRecurringMapper {

    fun toDomain(
        entity: RecurringEntity,
        category: Category?,
        account: Account?,
        creditCard: CreditCard?,
    ): Recurring

    fun toEntity(recurring: Recurring): RecurringEntity
}
