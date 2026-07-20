@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType = AccountType.ASSET,
    val currency: String = BASE_CURRENCY,
    val iconKey: String = "wallet",
    val isDefault: Boolean = false,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    // An account with history is closed, never deleted: the entries that reference
    // it stay valid and its real type is preserved. Categories and cards read their
    // own closure from here, through their accountId — one flag, one owner (D21).
    val isArchived: Boolean = false,
) {
    init {
        if (name.isEmpty()) {
            throw AccountException(AccountError.EMPTY_NAME)
        }
    }
}
