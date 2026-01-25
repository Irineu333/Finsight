@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model

import androidx.compose.ui.graphics.Color
import com.neoutils.finance.extension.safeOnDay
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val formats = DateFormats()

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
    val label get() = "${formats.yearMonth.format(dueMonth)} • ${status.label}"
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
        val label: String,
        val color: Color,
    ) {
        FUTURE(
            label = "Futura",
            color = Color(0xFF42A5F5)
        ),
        OPEN(
            label = "Aberta",
            color = Color(0xFFFFA726)
        ),
        CLOSED(
            label = "Fechada",
            color = Color(0xFFEF5350)
        ),
        PAID(
            label = "Paga",
            color = Color(0xFF66BB6A)
        ),
        RETROACTIVE(
            label = "Retroativa",
            color = Color(0xFF5C6BC0)
        );

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

