package com.neoutils.finsight.feature.transactions.model
import com.neoutils.finsight.feature.transactions.model.OperationInstallment

data class OperationInstallment(
    val id: Long,
    val count: Int,
    val number: Int,
    val totalAmount: Double,
) {
    val label = "$number/$count"
}