package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.TransactionError

class TransactionException(val error: TransactionError) : Exception(error.message)
