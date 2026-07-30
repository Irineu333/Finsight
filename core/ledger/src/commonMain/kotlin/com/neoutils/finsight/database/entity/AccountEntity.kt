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
    // No default: no row of the chart of accounts exists without somebody deciding
    // its currency (design D28), system rows included — which is what makes
    // `currency` mean the same thing on every line.
    val currency: String,
    val iconKey: String = "wallet",
    val isDefault: Boolean = false,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    // The single closure flag of the whole app: a category or a card is closed
    // when *its* account is (design D21). Closed accounts keep their history and
    // their real type; they are only hidden from the active selectors.
    val isArchived: Boolean = false,
) {
    // Persisted by Room's native enum support as TEXT, with no TypeConverter —
    // which is why adding a member changes no schema and needs no migration.
    enum class Type {
        ASSET,
        LIABILITY,
        INCOME,
        EXPENSE,
        EQUITY,
        CONVERSION
    }
}
