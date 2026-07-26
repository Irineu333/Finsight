package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.RecurringRetireError

class RecurringRetireException(val error: RecurringRetireError) : Exception(error.message)
