package com.neoutils.finsight.domain.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
data class CreditCard(
    val id: Long = 0,
    val name: String,
    val limit: Double,
    val closingDay: Int,
    val dueDay: Int,
    val iconKey: String = "card",
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)