package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.icons.CategoryLazyIcon

class CategoryMapper {
    fun toDomain(row: com.neoutils.finsight.database.dao.CategoryWithClosure): Category =
        toDomain(row.category).copy(isClosed = row.isClosed)

    fun toDomain(
        entity: CategoryEntity
    ): Category {
        return Category(
            id = entity.id,
            name = entity.name,
            icon = CategoryLazyIcon(entity.iconKey),
            type = toDomain(entity.type),
            createdAt = entity.createdAt,
            accountId = entity.accountId,
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
            iconKey = domain.icon.key,
            type = toEntity(domain.type),
            createdAt = domain.createdAt,
            accountId = domain.accountId,
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
