package com.neoutils.finsight.domain.model

data class Installment(
    val id: Long = 0,
    val count: Int,
    val number: Int,
    val totalAmount: Double,
) {
    val label get() = "$number/$count"
}