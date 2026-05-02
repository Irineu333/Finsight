package com.neoutils.finsight.feature.categories.error

enum class CategoryError(val message: String) {
    EMPTY_NAME(message = "Category name cannot be empty"),
    ALREADY_EXIST(message = "Category name already exists"),
}
