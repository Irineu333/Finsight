package com.neoutils.finsight.feature.transactions.model
import com.neoutils.finsight.feature.transactions.model.OperationRecurring

data class OperationRecurring(
    val id: Long,
    val recurringLabel: String,
    val cycleNumber: Int,
) {
    val label = "$recurringLabel • $cycleNumber"
}