package com.neoutils.finance.domain.exception

data class ReopenInvoiceException(
    override val message: String
) : Exception(message)
