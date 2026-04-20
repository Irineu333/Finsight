package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.CreditCardError

class CreditCardException(
    val error: CreditCardError,
) : Exception(error.message)