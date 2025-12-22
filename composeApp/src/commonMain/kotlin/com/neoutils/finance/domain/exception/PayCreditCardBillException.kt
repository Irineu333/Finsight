package com.neoutils.finance.domain.exception

data class PayCreditCardBillException(
    override val message: String
) : Exception(message)
