package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.error.CategoryError
import com.neoutils.finsight.feature.categories.impl.resources.Res
import com.neoutils.finsight.feature.categories.impl.resources.category_error_already_exist
import com.neoutils.finsight.feature.categories.impl.resources.category_error_empty_name
import com.neoutils.finsight.util.UiText

fun CategoryError.toUiText() = when (this) {
    CategoryError.EMPTY_NAME -> UiText.Res(Res.string.category_error_empty_name)
    CategoryError.ALREADY_EXIST -> UiText.Res(Res.string.category_error_already_exist)
}
