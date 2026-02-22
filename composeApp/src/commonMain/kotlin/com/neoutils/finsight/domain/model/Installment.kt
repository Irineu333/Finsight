package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.toMoneyFormat

data class Installment(
    val id: Long = 0,
    val count: Int,
    val number: Int,
    val totalAmount: Double,
) {
    val label get() = "$number/$count"
    val totalLabel get() = totalAmount.toMoneyFormat()
}