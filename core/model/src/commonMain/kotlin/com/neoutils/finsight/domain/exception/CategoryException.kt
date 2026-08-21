package com.neoutils.finsight.domain.exception

import com.neoutils.finsight.domain.error.CategoryError

class CategoryException(val error: CategoryError) : Exception(error.message)
