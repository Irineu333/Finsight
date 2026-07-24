@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Entity(
    tableName = "credit_cards",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.NO_ACTION
        ),
    ],
    indices = [
        Index(value = ["accountId"]),
    ]
)
data class CreditCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val limit: Double,
    val closingDay: Int,
    val dueDay: Int,
    val iconKey: String = "card",
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    // The card's LIABILITY row in the chart of accounts, created with the card.
    val accountId: Long = 0,
)
