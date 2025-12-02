package com.neoutils.finance.data.mapper

import com.neoutils.finance.data.CategoryEntity
import com.neoutils.finance.domain.model.Category

fun CategoryEntity.toDomain(): Category {
    return Category(
        id = id,
        name = name,
        key = key,
        type = type.toDomain(),
        createdAt = createdAt
    )
}

fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        key = key,
        type = type.toEntity(),
        createdAt = createdAt
    )
}

fun CategoryEntity.CategoryType.toDomain(): Category.CategoryType {
    return when (this) {
        CategoryEntity.CategoryType.INCOME -> Category.CategoryType.INCOME
        CategoryEntity.CategoryType.EXPENSE -> Category.CategoryType.EXPENSE
    }
}

fun Category.CategoryType.toEntity(): CategoryEntity.CategoryType {
    return when (this) {
        Category.CategoryType.INCOME -> CategoryEntity.CategoryType.INCOME
        Category.CategoryType.EXPENSE -> CategoryEntity.CategoryType.EXPENSE
    }
}
