package com.neoutils.finance.domain.exception

data class UpdateCreditCardException(
    override val message: String
) : Exception(message)
