package com.neoutils.finance.domain.exception

import com.neoutils.finance.domain.error.BuildTransactionError

class BuildTransactionException(val error: BuildTransactionError) : Exception(error.message)