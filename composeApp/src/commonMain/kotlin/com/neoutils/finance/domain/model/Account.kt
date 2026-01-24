@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model

import com.neoutils.finance.domain.error.AccountError
import com.neoutils.finance.domain.exception.AccountException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class Account(
    val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    init {
        if (name.isEmpty()) {
            throw AccountException(AccountError.EMPTY_NAME)
        }
    }
}
