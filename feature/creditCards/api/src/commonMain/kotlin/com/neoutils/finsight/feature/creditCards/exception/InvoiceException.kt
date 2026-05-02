package com.neoutils.finsight.feature.creditCards.exception

import com.neoutils.finsight.feature.creditCards.error.InvoiceError

class InvoiceException(val error: InvoiceError) : Exception(error.message)