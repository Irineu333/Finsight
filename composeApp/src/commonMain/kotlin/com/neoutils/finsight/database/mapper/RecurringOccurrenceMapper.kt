package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.RecurringOccurrenceEntity
import com.neoutils.finsight.domain.model.RecurringOccurrence

class RecurringOccurrenceMapper {

    fun toDomain(entity: RecurringOccurrenceEntity): RecurringOccurrence =
        RecurringOccurrence(
            id = entity.id,
            recurringId = entity.recurringId,
            cycleNumber = entity.cycleNumber,
            yearMonth = entity.yearMonth,
            status = when (entity.status) {
                RecurringOccurrenceEntity.Status.CONFIRMED -> RecurringOccurrence.Status.CONFIRMED
                RecurringOccurrenceEntity.Status.SKIPPED -> RecurringOccurrence.Status.SKIPPED
            },
            operationId = entity.operationId,
            effectiveDate = entity.effectiveDate,
            handledAt = entity.handledAt,
        )

    fun toEntity(domain: RecurringOccurrence): RecurringOccurrenceEntity =
        RecurringOccurrenceEntity(
            id = domain.id,
            recurringId = domain.recurringId,
            cycleNumber = domain.cycleNumber,
            yearMonth = domain.yearMonth,
            status = when (domain.status) {
                RecurringOccurrence.Status.CONFIRMED -> RecurringOccurrenceEntity.Status.CONFIRMED
                RecurringOccurrence.Status.SKIPPED -> RecurringOccurrenceEntity.Status.SKIPPED
            },
            operationId = domain.operationId,
            effectiveDate = domain.effectiveDate,
            handledAt = domain.handledAt,
        )
}
