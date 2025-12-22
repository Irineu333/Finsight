package com.neoutils.finance.domain.exception

data class CreateInvoiceException(
    override val message: String
) : Exception(message)
