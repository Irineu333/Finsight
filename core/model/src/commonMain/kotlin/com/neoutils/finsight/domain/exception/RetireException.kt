package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.RetireError

class RetireException(val error: RetireError) : Exception(error.message)
