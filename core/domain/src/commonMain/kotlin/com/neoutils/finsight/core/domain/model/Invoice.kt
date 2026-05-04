@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Invoice(
    val id: Long = 0,
    val creditCardId: Long,
    val openingMonth: YearMonth,
    val closingMonth: YearMonth,
    val dueMonth: YearMonth,
    val status: Status,
    val createdAt: Instant = Clock.System.now(),
    val openedAt: LocalDate? = null,
    val closedAt: LocalDate? = null,
    val paidAt: LocalDate? = null
) {
    val isClosable get() = when(status) {
        Status.OPEN -> true
        Status.RETROACTIVE -> true
        else -> false
    }

    val isPayable get() = when(status) {
        Status.CLOSED -> true
        Status.RETROACTIVE -> true
        else -> false
    }

    enum class Status(
        val colorValue: Long,
    ) {
        FUTURE(colorValue = 0xFF42A5F5L),
        OPEN(colorValue = 0xFFFFA726L),
        CLOSED(colorValue = 0xFFEF5350L),
        PAID(colorValue = 0xFF66BB6AL),
        RETROACTIVE(colorValue = 0xFF5C6BC0L);

        val isFuture: Boolean
            get() = this == FUTURE

        val isOpen: Boolean
            get() = this == OPEN

        val isClosed: Boolean
            get() = this == CLOSED

        val isPaid: Boolean
            get() = this == PAID

        val isRetroactive: Boolean
            get() = this == RETROACTIVE

        val isBlocked: Boolean
            get() = this == CLOSED || this == PAID

        val isEditable: Boolean
            get() = this == RETROACTIVE ||
                    this == OPEN ||
                    this == FUTURE

        val isDeletable: Boolean
            get() = this == FUTURE ||
                    this == RETROACTIVE
    }

    init {
        require(closingMonth > openingMonth) {
            "Closing month must be after opening month"
        }
        require(dueMonth >= closingMonth) {
            "Due month must be equal to or after closing month"
        }
    }
}
