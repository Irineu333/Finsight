package com.neoutils.finance.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installments")
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val count: Int,
    val totalAmount: Double,
)
