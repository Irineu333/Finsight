@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        ),
    ],
    indices = [
        Index(value = ["accountId"]),
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconKey: String,
    val type: Type,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val accountId: Long? = null,
) {
    enum class Type {
        INCOME,
        EXPENSE
    }
}
