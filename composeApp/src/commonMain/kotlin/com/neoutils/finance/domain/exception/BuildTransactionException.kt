package com.neoutils.finance.domain.exception

data class BuildTransactionException(
    override val message: String
) : Exception(message)
