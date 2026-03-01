@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.category_default_benefits
import com.neoutils.finsight.resources.category_default_education
import com.neoutils.finsight.resources.category_default_entertainment
import com.neoutils.finsight.resources.category_default_food
import com.neoutils.finsight.resources.category_default_freelance
import com.neoutils.finsight.resources.category_default_health
import com.neoutils.finsight.resources.category_default_housing
import com.neoutils.finsight.resources.category_default_investments
import com.neoutils.finsight.resources.category_default_market
import com.neoutils.finsight.resources.category_default_refund
import com.neoutils.finsight.resources.category_default_salary
import com.neoutils.finsight.resources.category_default_subscriptions
import com.neoutils.finsight.resources.category_default_transport
import com.neoutils.finsight.resources.category_default_travel
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.util.CategoryIcon
import com.neoutils.finsight.util.UiText
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CreateDefaultCategoriesUseCase(
    private val categoryRepository: ICategoryRepository,
) {
    private data class Template(
        val name: UiText,
        val icon: CategoryIcon,
        val type: Category.Type,
    )

    private val templates = listOf(
        Template(UiText.Res(Res.string.category_default_salary), CategoryIcon.MONEY, Category.Type.INCOME),
        Template(UiText.Res(Res.string.category_default_freelance), CategoryIcon.WORK, Category.Type.INCOME),
        Template(UiText.Res(Res.string.category_default_investments), CategoryIcon.BUSINESS, Category.Type.INCOME),
        Template(UiText.Res(Res.string.category_default_benefits), CategoryIcon.GIFT, Category.Type.INCOME),
        Template(UiText.Res(Res.string.category_default_refund), CategoryIcon.DEFAULT, Category.Type.INCOME),
        Template(UiText.Res(Res.string.category_default_housing), CategoryIcon.HOME, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_food), CategoryIcon.RESTAURANT, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_market), CategoryIcon.SHOPPING_CART, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_transport), CategoryIcon.CAR, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_health), CategoryIcon.HEALTH, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_education), CategoryIcon.SCHOOL, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_entertainment), CategoryIcon.MOVIE, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_subscriptions), CategoryIcon.WIFI, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_travel), CategoryIcon.FLIGHT, Category.Type.EXPENSE),
    )

    suspend operator fun invoke() {
        val createdAtBase = Clock.System.now().toEpochMilliseconds()

        templates.forEachIndexed { index, template ->
            categoryRepository.insert(
                Category(
                    name = template.name.asString(),
                    icon = CategoryLazyIcon(template.icon.key),
                    type = template.type,
                    createdAt = createdAtBase + index,
                )
            )
        }
    }
}
