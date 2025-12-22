package com.neoutils.finance.domain.exception

data class OpenInvoiceException(
    override val message: String
) : Exception(message)
