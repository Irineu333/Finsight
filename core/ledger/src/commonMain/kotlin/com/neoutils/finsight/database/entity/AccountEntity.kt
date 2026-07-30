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
) {
    /**
     * The stored side of `AccountType`, member for member.
     *
     * Adding a member here does **not** change the schema and needs no migration:
     * there is no `TypeConverter` for this enum, so Room persists it natively as
     * `TEXT` holding the constant's name. A row simply never carries a name the app
     * did not write.
     */
    enum class Type {
        ASSET,
        LIABILITY,
        INCOME,
        EXPENSE,
        EQUITY,
        CONVERSION
    }
}
