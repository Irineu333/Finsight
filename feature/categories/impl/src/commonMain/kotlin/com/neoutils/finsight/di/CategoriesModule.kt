package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.CategoryMapper
import com.neoutils.finsight.database.repository.CategoryRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CreateDefaultCategoriesUseCase
import com.neoutils.finsight.domain.usecase.ValidateCategoryNameUseCase
import com.neoutils.finsight.feature.categories.api.CategoriesEntry
import com.neoutils.finsight.feature.categories.impl.CategoriesEntryImpl
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormViewModel
import com.neoutils.finsight.ui.modal.deleteCategory.DeleteCategoryViewModel
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryViewModel
import com.neoutils.finsight.ui.screen.categories.CategoriesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val categoriesModule = module {
    single<ICategoryRepository> {
        CategoryRepository(
            dao = get(),
            mapper = get(),
        )
    }
    factory { CategoryMapper() }

    factory { CalculateCategorySpendingUseCase() }
    factory { CalculateCategoryIncomeUseCase() }
    factory { ValidateCategoryNameUseCase(repository = get()) }
    factory { CreateDefaultCategoriesUseCase(categoryRepository = get()) }

    single<CategoriesEntry> { CategoriesEntryImpl() }

    viewModel {
        CategoriesViewModel(
            categoryRepository = get(),
            createDefaultCategories = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        CategoryFormViewModel(
            category = it.getOrNull(),
            initialType = it.getOrNull(),
            repository = get(),
            validateCategoryName = get(),
            modalManager = get(),
            debounceManager = get(),
            analytics = get(),
        )
    }

    viewModel {
        DeleteCategoryViewModel(
            category = it.get(),
            repository = get(),
            modalManager = get(),
            analytics = get(),
        )
    }

    viewModel {
        ViewCategoryViewModel(
            category = it.get(),
            categoryRepository = get(),
            transactionRepository = get(),
        )
    }
}
