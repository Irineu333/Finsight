package com.neoutils.finsight.feature.categories.extension

import com.neoutils.finsight.feature.categories.error.CategoryError
import com.neoutils.finsight.feature.categories.resources.Res
import com.neoutils.finsight.feature.categories.resources.category_error_already_exist
import com.neoutils.finsight.feature.categories.resources.category_error_empty_name
import com.neoutils.finsight.core.ui.util.UiText

fun CategoryError.toUiText() = when (this) {
    CategoryError.EMPTY_NAME -> UiText.Res(Res.string.category_error_empty_name)
    CategoryError.ALREADY_EXIST -> UiText.Res(Res.string.category_error_already_exist)
}
