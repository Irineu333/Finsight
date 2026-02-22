@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
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
