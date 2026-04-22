package com.neoutils.finsight.domain.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
data class Account(
    val id: Long = 0,
    val name: String,
    val iconKey: String = "wallet",
    val isDefault: Boolean = false,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)