@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class CreditCard(
        val id: Long = 0,
        val name: String,
        val limit: Double,
        val closingDay: Int? = null,
        val createdAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    init {
        require(closingDay == null || closingDay in 1..28) {
            "Closing day must be between 1 and 28"
        }
    }
}
