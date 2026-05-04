package com.neoutils.finsight.feature.transactions.model

data class OperationInstallment(
    val id: Long,
    val count: Int,
    val number: Int,
    val totalAmount: Double,
) {
    val label = "$number/$count"
}
