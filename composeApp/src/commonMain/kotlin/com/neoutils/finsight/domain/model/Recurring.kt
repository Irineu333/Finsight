package com.neoutils.finsight.domain.model

data class Recurring(
    val id: Long = 0,
    val type: Transaction.Type,
    val amount: Double,
    val title: String?,
    val dayOfMonth: Int,
    val category: Category?,
    val account: Account?,
    val creditCard: CreditCard?,
    val createdAt: Long,
    val isActive: Boolean = true,
)
