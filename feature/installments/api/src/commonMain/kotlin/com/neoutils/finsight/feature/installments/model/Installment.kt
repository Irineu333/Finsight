package com.neoutils.finsight.feature.installments.model

data class Installment(
    val id: Long = 0,
    val count: Int,
    val totalAmount: Double,
)
