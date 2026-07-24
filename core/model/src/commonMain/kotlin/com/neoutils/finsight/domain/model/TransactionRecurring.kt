package com.neoutils.finsight.domain.model

data class TransactionRecurring(
    val instance: Recurring,
    val cycleNumber: Int,
) {
    val id = instance.id
    val label = "${instance.label} • $cycleNumber"
}
