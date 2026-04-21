package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.BuildTransactionError

class BuildTransactionException(val error: BuildTransactionError) : Exception(error.message)
