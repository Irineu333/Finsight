package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.RecurringEntity
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring

interface IRecurringMapper {

    fun toDomain(
        entity: RecurringEntity,
        category: Category?,
        account: Account?,
        creditCard: CreditCard?,
    ): Recurring

    fun toEntity(recurring: Recurring): RecurringEntity
}
