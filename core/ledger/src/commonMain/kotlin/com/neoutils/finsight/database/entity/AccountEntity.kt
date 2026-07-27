@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: Type = Type.ASSET,
    val currency: String = "BRL",
    val iconKey: String = "wallet",
    val isDefault: Boolean = false,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    // The single closure flag of the whole app: a category or a card is closed
    // when *its* account is (design D21). Closed accounts keep their history and
    // their real type; they are only hidden from the active selectors.
    val isArchived: Boolean = false,
    // Affordance only: whether this account offers the yield line and the launch
    // path. No ledger read consults it — the separation of yield is done by the
    // dimension (design D2).
    val yieldsInterest: Boolean = false,
) {
    enum class Type {
        ASSET,
        LIABILITY,
        INCOME,
        EXPENSE,
        EQUITY
    }
}
