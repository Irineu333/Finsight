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
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    enum class Type {
        ASSET,
        LIABILITY,
        INCOME,
        EXPENSE,
        EQUITY
    }
}
