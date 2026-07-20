package com.neoutils.finsight.domain.model

import kotlinx.serialization.Serializable

/**
 * Where the user is putting the money: an account or a credit card.
 *
 * Retained as **input vocabulary** (design D4) — it is the account-vs-card picker
 * and a navigation filter, not a property of a ledger leg, where `ASSET` vs
 * `LIABILITY` already determines it.
 *
 * ⚠️ The constant names are published wire format (see [TransactionType]).
 */
@Serializable
enum class TransactionTarget {
    ACCOUNT,
    CREDIT_CARD;

    val isAccount: Boolean get() = this == ACCOUNT
    val isCreditCard: Boolean get() = this == CREDIT_CARD
}
