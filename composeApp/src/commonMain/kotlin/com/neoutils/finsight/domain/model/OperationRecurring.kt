package com.neoutils.finsight.domain.model

data class OperationRecurring(
    val instance: Recurring,
    val cycleNumber: Int,
) {
    val id = instance.id
    val label = "${instance.title} • $cycleNumber"
}