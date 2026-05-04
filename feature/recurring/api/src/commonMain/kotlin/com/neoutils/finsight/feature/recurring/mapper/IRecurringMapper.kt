package com.neoutils.finsight.feature.recurring.mapper

import com.neoutils.finsight.core.database.entity.RecurringEntity
import com.neoutils.finsight.feature.recurring.model.Recurring

interface IRecurringMapper {

    fun toDomain(entity: RecurringEntity): Recurring

    fun toEntity(recurring: Recurring): RecurringEntity
}
