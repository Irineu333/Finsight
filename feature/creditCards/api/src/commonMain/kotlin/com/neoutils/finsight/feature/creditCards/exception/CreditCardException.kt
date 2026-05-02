package com.neoutils.finsight.feature.creditCards.exception

import com.neoutils.finsight.feature.creditCards.error.CreditCardError
class CreditCardException(
    val error: CreditCardError,
) : Exception(error.message)