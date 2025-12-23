@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class Invoice(
    val id: Long = 0,
    val creditCard: CreditCard,
    val openingMonth: YearMonth,
    val closingMonth: YearMonth,
    val status: Status,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val closedAt: Long? = null,
    val paidAt: Long? = null
) {
    enum class Status(
        val label: String,
        val color: Color,
    ) {
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
        );

        val isOpen: Boolean
            get() = this == OPEN
    }

    init {
        require(closingMonth > openingMonth) {
            "Closing month must be after opening month"
        }
    }
}

