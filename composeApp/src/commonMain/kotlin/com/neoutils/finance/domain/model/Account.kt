@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class Account(
    val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    init {
        require(name.isNotBlank()) { "Account name cannot be blank" }
    }
}
