package com.neoutils.finance.domain.model

data class Installment(
    val count: Int,
    val number: Int,
    val groupUuid: String,
) {
    val label get() = "$number/$count"
}