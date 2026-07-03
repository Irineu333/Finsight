package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.RecurringError

class RecurringException(val error: RecurringError) : Exception(error.message)
