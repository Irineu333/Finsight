package com.neoutils.finsight.feature.categories.di

import com.neoutils.finsight.feature.categories.mapper.CategoryMapper
import com.neoutils.finsight.feature.categories.repository.CategoryRepository
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.categories.usecase.CreateDefaultCategoriesUseCase
import com.neoutils.finsight.feature.categories.usecase.ValidateCategoryNameUseCase
import com.neoutils.finsight.feature.categories.modal.categoryForm.CategoryFormModalEntry
import com.neoutils.finsight.feature.categories.entryPoint.CategoryFormModalEntryImpl
import com.neoutils.finsight.feature.categories.modal.viewCategory.ViewCategoryModalEntry
import com.neoutils.finsight.feature.categories.entryPoint.ViewCategoryModalEntryImpl
import com.neoutils.finsight.feature.categories.modal.categoryForm.CategoryFormViewModel
import com.neoutils.finsight.feature.categories.modal.deleteCategory.DeleteCategoryViewModel
import com.neoutils.finsight.feature.categories.modal.viewCategory.ViewCategoryViewModel
import com.neoutils.finsight.feature.categories.screen.CategoriesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val categoriesModule = module {

    single<CategoryFormModalEntry> { CategoryFormModalEntryImpl() }
    single<ViewCategoryModalEntry> { ViewCategoryModalEntryImpl() }

    single { CategoryMapper() }

    single<ICategoryRepository> {
        CategoryRepository(
            dao = get(),
            mapper = get(),
        )
    }

    factory {
        ValidateCategoryNameUseCase(
            repository = get(),
        )
    }

    factory {
        CreateDefaultCategoriesUseCase(
            categoryRepository = get(),
        )
    }

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
