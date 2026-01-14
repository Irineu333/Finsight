package com.neoutils.finance.domain.model

import com.neoutils.finance.extension.toMoneyFormat

data class Installment(
    val count: Int,
    val number: Int,
    val groupUuid: String,
    val totalAmount: Double,
) {
    val label get() = "$number/$count"
    val totalLabel get() = totalAmount.toMoneyFormat()
}