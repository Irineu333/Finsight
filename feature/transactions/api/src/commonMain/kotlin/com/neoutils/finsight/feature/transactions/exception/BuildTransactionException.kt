package com.neoutils.finsight.feature.transactions.exception

import com.neoutils.finsight.feature.transactions.error.BuildTransactionError
class BuildTransactionException(val error: BuildTransactionError) : Exception(error.message)
