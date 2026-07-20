package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.CategoryMapper
import com.neoutils.finsight.database.repository.CategoryRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCaseImpl
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCaseImpl
import com.neoutils.finsight.domain.usecase.CreateDefaultCategoriesUseCase
import com.neoutils.finsight.domain.usecase.ValidateCategoryNameUseCase
import com.neoutils.finsight.feature.categories.api.CategoriesEntry
import com.neoutils.finsight.feature.categories.impl.CategoriesEntryImpl
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormViewModel
import com.neoutils.finsight.domain.usecase.CloseCategoryUseCase
import com.neoutils.finsight.domain.usecase.DeleteCategoryUseCase
import com.neoutils.finsight.ui.modal.closeCategory.CloseCategoryViewModel
import com.neoutils.finsight.ui.modal.deleteCategory.DeleteCategoryViewModel
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryViewModel
import com.neoutils.finsight.ui.screen.categories.CategoriesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val categoriesModule = module {
    single<ICategoryRepository> {
        CategoryRepository(
            database = get(),
            dao = get(),
            accountDao = get(),
            mapper = get(),
        )
    }
    factory { CategoryMapper() }

    factory<CalculateCategorySpendingUseCase> {
        CalculateCategorySpendingUseCaseImpl(categoryRepository = get(), entryRepository = get())
    }
    factory<CalculateCategoryIncomeUseCase> {
        CalculateCategoryIncomeUseCaseImpl(categoryRepository = get(), entryRepository = get())
    }
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

    factory {
        DeleteCategoryUseCase(
            categoryRepository = get(),
            accountRepository = get(),
            deleteAccountUseCase = get(),
        )
    }

    factory {
        CloseCategoryUseCase(
            accountRepository = get(),
            closeAccountUseCase = get(),
        )
    }

    viewModel {
        DeleteCategoryViewModel(
            category = it.get(),
            deleteCategoryUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        CloseCategoryViewModel(
            category = it.get(),
            closeCategoryUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        ViewCategoryViewModel(
            categoryId = it.get(),
            categoryRepository = get(),
            entryRepository = get(),
            crashlytics = get(),
        )
    }
}
