package com.neoutils.finsight.feature.budgets.exception

import com.neoutils.finsight.feature.budgets.error.BudgetError

class BudgetException(
    val error: BudgetError
) : Exception(error.message)
