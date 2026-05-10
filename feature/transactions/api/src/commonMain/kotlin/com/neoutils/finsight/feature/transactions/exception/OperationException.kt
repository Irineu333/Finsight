package com.neoutils.finsight.feature.transactions.exception

import com.neoutils.finsight.feature.transactions.error.OperationError

class OperationException(val error: OperationError) : Exception(error.message)
