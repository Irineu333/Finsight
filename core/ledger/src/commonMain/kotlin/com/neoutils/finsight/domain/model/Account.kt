@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model

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
    // it stay valid and its real type is preserved. A card reads its own closure
    // from here, through its accountId — one flag, one owner (D21). A category does
    // not: it owns no account, so it owns its flag (D4).
    val isArchived: Boolean = false,
) {
    init {
        // A last-resort guard, not the validation the user sees: an empty name is
        // refused by `ValidateAccountNameUseCase` with a typed error long before a
        // row is built. Nothing catches this — reaching it is a defect — so it says
        // so in the plainest way rather than borrowing a facade's error vocabulary.
        require(name.isNotEmpty()) { "An account must have a name." }
    }
}
