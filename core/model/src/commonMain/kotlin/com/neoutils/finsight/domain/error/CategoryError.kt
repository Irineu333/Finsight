package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.category_error_already_exist
import com.neoutils.finsight.resources.category_error_empty_name
import com.neoutils.finsight.util.UiText

enum class CategoryError(val message: String) {
    EMPTY_NAME(message = "Category name cannot be empty"),
    ALREADY_EXIST(message = "Category name already exists"),
}

fun CategoryError.toUiText() = when (this) {
    CategoryError.EMPTY_NAME -> UiText.Res(Res.string.category_error_empty_name)
    CategoryError.ALREADY_EXIST -> UiText.Res(Res.string.category_error_already_exist)
}