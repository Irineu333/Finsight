package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

data class RecurringOccurrence(
    val id: Long = 0,
    val recurringId: Long,
    val cycleNumber: Int,
    val yearMonth: YearMonth,
    val status: Status,
    val operationId: Long? = null,
    val effectiveDate: LocalDate,
    val handledAt: Long,
) {
    enum class Status {
        CONFIRMED,
        SKIPPED,
    }
}
