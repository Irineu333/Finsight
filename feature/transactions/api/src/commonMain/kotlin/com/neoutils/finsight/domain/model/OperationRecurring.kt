package com.neoutils.finsight.domain.model

data class OperationRecurring(
    val id: Long,
    val recurringLabel: String,
    val cycleNumber: Int,
) {
    val label = "$recurringLabel • $cycleNumber"
}