package com.neoutils.finsight.domain.model

data class OperationInstallment(
    val instance: Installment,
    val number: Int,
) {
    val id = instance.id
    val label = "$number/${instance.count}"
}