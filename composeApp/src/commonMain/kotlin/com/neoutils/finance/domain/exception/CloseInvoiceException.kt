package com.neoutils.finance.domain.exception

data class CloseInvoiceException(
    override val message: String
) : Exception(message)