package com.neoutils.finsight.feature.categories.exception

import com.neoutils.finsight.feature.categories.error.CategoryError

class CategoryException(
    val error: CategoryError
) : Exception(error.message)
