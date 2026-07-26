@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Entity(
    tableName = "recurring",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["creditCardId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
        Index(value = ["creditCardId"]),
    ]
)
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: Type,
    val amount: Double,
    val title: String?,
    val dayOfMonth: Int,
    val categoryId: Long?,
    val accountId: Long?,
    val creditCardId: Long?,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    /**
     * The **inverse** of `Recurring.isArchived`: stored `true` means not archived.
     *
     * The name diverges from the meaning on purpose. The domain and the UI speak
     * `isArchived`, like every other archivable facade; renaming the column would
     * take a migration (rename plus default inversion) that this change is too
     * small to justify, so `RecurringMapper` inverts on both sides and is the only
     * place that does (design D1).
     *
     * Debt with an owner: when the rename comes, it touches this field, two lines
     * of the mapper, and the migration SQL/tests that name the column — and nothing
     * in domain, use case, ViewModel or screen.
     */
    val isActive: Boolean = true,
) {
    enum class Type { EXPENSE, INCOME }
}
