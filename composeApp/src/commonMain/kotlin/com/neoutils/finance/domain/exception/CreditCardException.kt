package com.neoutils.finance.domain.exception

import com.neoutils.finance.domain.error.CreditCardError

class CreditCardException(
    val error: CreditCardError,
) : Exception(error.message)