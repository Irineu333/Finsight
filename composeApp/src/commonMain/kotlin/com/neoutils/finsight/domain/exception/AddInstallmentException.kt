package com.neoutils.finsight.domain.exception

data class AddInstallmentException(
    override val message: String
) : Exception(message)
