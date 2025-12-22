package com.neoutils.finance.domain.exception

data class RegisterCreditCardException(
    override val message: String
) : Exception(message)