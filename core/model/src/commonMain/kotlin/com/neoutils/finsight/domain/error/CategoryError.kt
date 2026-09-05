package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.category_error_already_exist
import com.neoutils.finsight.resources.category_error_empty_name
import com.neoutils.finsight.resources.category_error_not_found
import com.neoutils.finsight.util.UiText

enum class CategoryError(val message: String) {
    EMPTY_NAME(message = "Category name cannot be empty"),
    ALREADY_EXIST(message = "Category name already exists"),

    /**
     * The identity handed to the operation matches no category. Every use case
     * resolves the category when it runs, so this is the refusal a caller gets for an
     * identifier that was never valid — and for one that stopped being valid between
     * the moment it was read and the moment it was used.
     */
    NOT_FOUND(message = "Category not found"),
}

fun CategoryError.toUiText() = when (this) {
    CategoryError.EMPTY_NAME -> UiText.Res(Res.string.category_error_empty_name)
    CategoryError.ALREADY_EXIST -> UiText.Res(Res.string.category_error_already_exist)
    CategoryError.NOT_FOUND -> UiText.Res(Res.string.category_error_not_found)
}
