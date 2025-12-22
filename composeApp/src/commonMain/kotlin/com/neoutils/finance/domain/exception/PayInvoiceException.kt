package com.neoutils.finance.domain.exception

data class PayInvoiceException(
    override val message: String
) : Exception(message)