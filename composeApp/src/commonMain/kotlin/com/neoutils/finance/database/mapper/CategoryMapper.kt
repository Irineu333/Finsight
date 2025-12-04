package com.neoutils.finance.database.mapper

import com.neoutils.finance.database.entity.CategoryEntity
import com.neoutils.finance.domain.model.Category

class CategoryMapper {
    fun toDomain(
        entity: CategoryEntity
    ): Category {
        return Category(
            id = entity.id,
            name = entity.name,
            key = entity.key,
            type = toDomain(entity.type),
            createdAt = entity.createdAt
        )
    }

    fun toDomain(
        type: CategoryEntity.Type
    ): Category.Type {
        return when (type) {
            CategoryEntity.Type.INCOME -> Category.Type.INCOME
            CategoryEntity.Type.EXPENSE -> Category.Type.EXPENSE
        }
    }

    fun toEntity(
        domain: Category
    ): CategoryEntity {
        return CategoryEntity(
            id = domain.id,
            name = domain.name,
            key = domain.key,
            type = toEntity(domain.type),
            createdAt = domain.createdAt
        )
    }

    fun toEntity(
        type: Category.Type
    ): CategoryEntity.Type {
        return when (type) {
            Category.Type.INCOME -> CategoryEntity.Type.INCOME
            Category.Type.EXPENSE -> CategoryEntity.Type.EXPENSE
        }
    }
}
