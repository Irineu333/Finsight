@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model

import androidx.compose.ui.graphics.Color
import com.neoutils.finsight.extension.safeOnDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Invoice(
    val id: Long = 0,
    val creditCard: CreditCard,
    val openingMonth: YearMonth,
    val closingMonth: YearMonth,
    val dueMonth: YearMonth,
    val status: Status,
    val createdAt: Instant = Clock.System.now(),
    val openedAt: LocalDate? = null,
    val closedAt: LocalDate? = null,
    val paidAt: LocalDate? = null
) {
    val openingDate get() = openingMonth.safeOnDay(creditCard.closingDay)
    val closingDate get() = closingMonth.safeOnDay(creditCard.closingDay)
    val dueDate get() = dueMonth.safeOnDay(creditCard.dueDay)

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
        val color: Color,
    ) {
        FUTURE(color = Color(0xFF42A5F5)),
        OPEN(color = Color(0xFFFFA726)),
        CLOSED(color = Color(0xFFEF5350)),
        PAID(color = Color(0xFF66BB6A)),
        RETROACTIVE(color = Color(0xFF5C6BC0));

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

