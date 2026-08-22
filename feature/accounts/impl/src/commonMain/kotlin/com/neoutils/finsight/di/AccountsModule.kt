@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.AccountMapper
import com.neoutils.finsight.database.repository.AccountRepository
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.ArchiveAccountUseCase
import com.neoutils.finsight.domain.usecase.ArchiveAccountUseCaseImpl
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCaseImpl
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCase
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCaseImpl
import com.neoutils.finsight.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.EnsureYieldCategoryUseCase
import com.neoutils.finsight.domain.usecase.LaunchYieldUseCase
import com.neoutils.finsight.domain.usecase.SetDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.SuggestAccountIconUseCase
import com.neoutils.finsight.domain.usecase.SuggestAccountIconUseCaseImpl
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveAccountUseCase
import com.neoutils.finsight.domain.usecase.UpdateAccountUseCase
import com.neoutils.finsight.domain.usecase.UpdateTransferUseCase
import com.neoutils.finsight.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransferUseCase
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.feature.accounts.api.AccountsEntry
import com.neoutils.finsight.feature.accounts.impl.AccountsEntryImpl
import com.neoutils.finsight.ui.modal.accountForm.AccountFormViewModel
import com.neoutils.finsight.ui.modal.archiveAccount.ArchiveAccountViewModel
import com.neoutils.finsight.ui.modal.deleteAccount.DeleteAccountViewModel
import com.neoutils.finsight.ui.modal.editAccountBalance.EditAccountBalanceViewModel
import com.neoutils.finsight.ui.modal.launchYield.LaunchYieldViewModel
import com.neoutils.finsight.ui.modal.transferBetweenAccounts.TransferBetweenAccountsViewModel
import com.neoutils.finsight.ui.modal.viewAccount.ViewAccountViewModel
import com.neoutils.finsight.ui.screen.accounts.AccountsViewModel
import com.neoutils.finsight.ui.screen.archived.ArchivedAccountsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val accountsModule = module {
    single<IAccountRepository> {
        AccountRepository(
            dao = get(),
            mapper = get(),
        )
    }
    factory { AccountMapper() }

    factory { EnsureDefaultAccountUseCase(repository = get(), baseCurrencyRepository = get()) }
    factory { ValidateAccountNameUseCase(repository = get()) }
    factory { SetDefaultAccountUseCase(repository = get()) }
    factory {
        CreateAccountUseCase(
            repository = get(),
            validateAccountName = get(),
            setDefaultAccount = get(),
        )
    }
    factory {
        UpdateAccountUseCase(
            repository = get(),
            validateAccountName = get(),
            setDefaultAccount = get(),
        )
    }
    factory<GetAccountCurrenciesUseCase> {
        GetAccountCurrenciesUseCaseImpl(accountDao = get())
    }
    factory<ArchiveAccountUseCase> {
        ArchiveAccountUseCaseImpl(
            accountDao = get(),
            entryRepository = get(),
        )
    }
    factory<DeleteAccountUseCase> {
        DeleteAccountUseCaseImpl(
            accountRepository = get(),
            entryRepository = get(),
            recurringRepository = get(),
        )
    }
    factory<SuggestAccountIconUseCase> {
        SuggestAccountIconUseCaseImpl(accountRepository = get())
    }
    factory { UnarchiveAccountUseCase(repository = get()) }
    factory { EnsureYieldCategoryUseCase(categoryRepository = get()) }
    factory {
        LaunchYieldUseCase(
            transactionRepository = get(),
            ensureYieldCategory = get(),
        )
    }
    factory {
        AdjustBalanceUseCase(
            transactionRepository = get(),
            calculateBalanceUseCase = get(),
        )
    }
    factory {
        ValidateTransferUseCase(
            accountRepository = get(),
            clock = get(),
        )
    }
    factory {
        TransferBetweenAccountsUseCase(
            harvestExchangeRate = get(),
            transactionRepository = get(),
            validateTransfer = get(),
        )
    }
    factory {
        UpdateTransferUseCase(
            harvestExchangeRate = get(),
            transactionRepository = get(),
            validateTransfer = get(),
        )
    }

    single<AccountsEntry> { AccountsEntryImpl() }

    viewModel {
        AccountsViewModel(
            installmentRepository = get(),
            accountRepository = get(),
            transactionRepository = get(),
            categoryRepository = get(),
            entryRepository = get(),
            clock = get(),
            initialAccountId = it.getOrNull(),
        )
    }
    viewModel {
        AccountFormViewModel(
            account = it.getOrNull(),
            validateAccountName = get(),
            suggestAccountIcon = get(),
            baseCurrencyRepository = get(),
            currencyRepository = get(),
            createAccountUseCase = get(),
            updateAccountUseCase = get(),
            ensureYieldCategory = get(),
            modalManager = get(),
            debounceManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        DeleteAccountViewModel(
            account = it.get(),
            deleteAccountUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        ArchiveAccountViewModel(
            account = it.get(),
            archiveAccountUseCase = get(),
            entryRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        EditAccountBalanceViewModel(
            initialDate = it.get(),
            account = it.get(),
            adjustBalanceUseCase = get(),
            calculateBalanceUseCase = get(),
            accountRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
            clock = get(),
        )
    }
    viewModel {
        LaunchYieldViewModel(
            account = it.get(),
            launchYieldUseCase = get(),
            accountRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        ViewAccountViewModel(
            accountId = it.get(),
            accountRepository = get(),
            unarchiveAccount = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        ArchivedAccountsViewModel(
            accountRepository = get(),
        )
    }
    viewModel {
        TransferBetweenAccountsViewModel(
            initialSourceAccount = it.get(),
            transaction = it.getOrNull(),
            transferBetweenAccountsUseCase = get(),
            updateTransferUseCase = get(),
            suggestCrossCurrencyAmount = get(),
            accountRepository = get(),
            clock = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
}
