package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.BudgetError

class BudgetException(val error: BudgetError) : Exception(error.message)
