package com.neoutils.finsight.feature.recurring.exception

import com.neoutils.finsight.feature.recurring.error.RecurringError

class RecurringException(val error: RecurringError) : Exception(error.message)
