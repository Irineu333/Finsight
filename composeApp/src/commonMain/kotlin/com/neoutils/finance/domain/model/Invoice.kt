@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model

import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class Invoice(
    val id: Long = 0,
    val creditCardId: Long,
    val openingMonth: YearMonth,
    val closingMonth: YearMonth,
    val status: Status,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    enum class Status(val label: String) {
        OPEN("Aberta"),
        CLOSED("Fechada"),
        PAID("Paga")
    }

    init {
        require(closingMonth > openingMonth) {
            "Closing month must be after opening month"
        }
    }
}

