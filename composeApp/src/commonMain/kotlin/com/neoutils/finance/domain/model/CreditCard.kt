@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class CreditCard(
    val id: Long = 0,
    val name: String,
    val limit: Double,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)
