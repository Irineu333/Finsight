@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.context.bind
import arrow.core.raise.either
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.feature.categories.impl.resources.Res
import com.neoutils.finsight.feature.categories.impl.resources.category_default_benefits
import com.neoutils.finsight.feature.categories.impl.resources.category_default_education
import com.neoutils.finsight.feature.categories.impl.resources.category_default_entertainment
import com.neoutils.finsight.feature.categories.impl.resources.category_default_food
import com.neoutils.finsight.feature.categories.impl.resources.category_default_freelance
import com.neoutils.finsight.feature.categories.impl.resources.category_default_health
import com.neoutils.finsight.feature.categories.impl.resources.category_default_housing
import com.neoutils.finsight.feature.categories.impl.resources.category_default_investments
import com.neoutils.finsight.feature.categories.impl.resources.category_default_market
import com.neoutils.finsight.feature.categories.impl.resources.category_default_refund
import com.neoutils.finsight.feature.categories.impl.resources.category_default_salary
import com.neoutils.finsight.feature.categories.impl.resources.category_default_subscriptions
import com.neoutils.finsight.feature.categories.impl.resources.category_default_transport
import com.neoutils.finsight.feature.categories.impl.resources.category_default_travel
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.UiText
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CreateDefaultCategoriesUseCase(
    private val categoryRepository: ICategoryRepository,
) {
    private data class Template(
        val name: UiText,
        val icon: AppIcon,
        val type: Category.Type,
    )

    private val templates = listOf(
        Template(UiText.Res(Res.string.category_default_salary), AppIcon.MONEY, Category.Type.INCOME),
        Template(UiText.Res(Res.string.category_default_freelance), AppIcon.WORK, Category.Type.INCOME),
        Template(UiText.Res(Res.string.category_default_investments), AppIcon.BUSINESS, Category.Type.INCOME),
        Template(UiText.Res(Res.string.category_default_benefits), AppIcon.GIFT, Category.Type.INCOME),
        Template(UiText.Res(Res.string.category_default_refund), AppIcon.DEFAULT, Category.Type.INCOME),
        Template(UiText.Res(Res.string.category_default_housing), AppIcon.HOME, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_food), AppIcon.RESTAURANT, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_market), AppIcon.SHOPPING_CART, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_transport), AppIcon.CAR, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_health), AppIcon.HEALTH, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_education), AppIcon.SCHOOL, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_entertainment), AppIcon.MOVIE, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_subscriptions), AppIcon.WIFI, Category.Type.EXPENSE),
        Template(UiText.Res(Res.string.category_default_travel), AppIcon.FLIGHT, Category.Type.EXPENSE),
    )

    suspend operator fun invoke(): Either<Throwable, Unit> = either {
        val createdAtBase = Clock.System.now().toEpochMilliseconds()

        catch {
            templates.forEachIndexed { index, template ->
                categoryRepository.insert(
                    Category(
                        name = template.name.asString(),
                        iconKey = template.icon.key,
                        type = template.type,
                        createdAt = createdAtBase + index,
                    )
                )
            }
        }.bind()
    }
}
