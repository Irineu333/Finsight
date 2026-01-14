package com.neoutils.finance.domain.exception

data class AddInstallmentException(
    override val message: String
) : Exception(message)
