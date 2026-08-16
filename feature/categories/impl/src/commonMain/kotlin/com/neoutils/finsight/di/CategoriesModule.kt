package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.CategoryMapper
import com.neoutils.finsight.database.repository.CategoryRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCaseImpl
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCaseImpl
import com.neoutils.finsight.domain.usecase.CreateCategoryUseCase
import com.neoutils.finsight.domain.usecase.CreateCategoryUseCaseImpl
import com.neoutils.finsight.domain.usecase.CreateDefaultCategoriesUseCase
import com.neoutils.finsight.domain.usecase.UpdateCategoryUseCase
import com.neoutils.finsight.domain.usecase.UpdateCategoryUseCaseImpl
import com.neoutils.finsight.domain.usecase.ValidateCategoryNameUseCase
import com.neoutils.finsight.feature.categories.api.CategoriesEntry
import com.neoutils.finsight.feature.categories.impl.CategoriesEntryImpl
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormViewModel
import com.neoutils.finsight.domain.usecase.ArchiveCategoryUseCase
import com.neoutils.finsight.domain.usecase.ArchiveCategoryUseCaseImpl
import com.neoutils.finsight.domain.usecase.DeleteCategoryUseCase
import com.neoutils.finsight.domain.usecase.DeleteCategoryUseCaseImpl
import com.neoutils.finsight.domain.usecase.ResolveCategoryRetirabilityUseCase
import com.neoutils.finsight.domain.usecase.ResolveCategoryRetirabilityUseCaseImpl
import com.neoutils.finsight.domain.usecase.UnarchiveCategoryUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCategoryUseCaseImpl
import com.neoutils.finsight.ui.modal.archiveCategory.ArchiveCategoryViewModel
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
            dimensionDao = get(),
            mapper = get(),
        )
    }
    factory { CategoryMapper() }

    factory<CalculateCategorySpendingUseCase> {
        CalculateCategorySpendingUseCaseImpl(
            categoryRepository = get(),
            entryRepository = get(),
            consolidateMoney = get(),
        )
    }
    factory<CalculateCategoryIncomeUseCase> {
        CalculateCategoryIncomeUseCaseImpl(
            categoryRepository = get(),
            entryRepository = get(),
            consolidateMoney = get(),
        )
    }
    factory { ValidateCategoryNameUseCase(repository = get()) }
    factory { CreateDefaultCategoriesUseCase(categoryRepository = get()) }
    factory<CreateCategoryUseCase> {
        CreateCategoryUseCaseImpl(
            categoryRepository = get(),
            validateCategoryName = get(),
        )
    }
    factory<UpdateCategoryUseCase> {
        UpdateCategoryUseCaseImpl(
            categoryRepository = get(),
            validateCategoryName = get(),
        )
    }

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
            createCategory = get(),
            updateCategory = get(),
            validateCategoryName = get(),
            modalManager = get(),
            debounceManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    factory<ResolveCategoryRetirabilityUseCase> {
        ResolveCategoryRetirabilityUseCaseImpl(
            categoryRepository = get(),
            entryRepository = get(),
            budgetRepository = get(),
            recurringRepository = get(),
            accountRepository = get(),
        )
    }

    factory<DeleteCategoryUseCase> {
        DeleteCategoryUseCaseImpl(
            categoryRepository = get(),
            resolveRetirability = get(),
        )
    }

    factory<ArchiveCategoryUseCase> {
        ArchiveCategoryUseCaseImpl(
            categoryRepository = get(),
        )
    }

    factory<UnarchiveCategoryUseCase> {
        UnarchiveCategoryUseCaseImpl(
            categoryRepository = get(),
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
        ArchiveCategoryViewModel(
            category = it.get(),
            archiveCategoryUseCase = get(),
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
            resolveRetirability = get(),
            unarchiveCategory = get(),
            consolidateMoney = get(),
            observeConsolidationChanges = get(),
            crashlytics = get(),
        )
    }
}
