package com.neoutils.finsight.domain.model

data class OperationInstallment(
    val id: Long,
    val count: Int,
    val number: Int,
    val totalAmount: Double,
) {
    val label = "$number/$count"
}