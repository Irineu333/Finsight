package com.neoutils.finsight.domain.model

data class Installment(
    val id: Long = 0,
    val count: Int,
    val totalAmount: Double,
)
