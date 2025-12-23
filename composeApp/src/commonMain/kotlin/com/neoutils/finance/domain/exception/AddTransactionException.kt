package com.neoutils.finance.domain.exception

data class AddTransactionException(
    override val message: String
) : Exception(message)

