@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.AccountMapper
import com.neoutils.finsight.database.repository.AccountRepository
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustOpeningBalanceUseCase
import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.ArchiveAccountUseCase
import com.neoutils.finsight.domain.usecase.ArchiveAccountUseCaseImpl
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCase
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCaseImpl
import com.neoutils.finsight.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.SetDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.domain.usecase.UpdateAccountUseCase
import com.neoutils.finsight.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.feature.accounts.api.AccountsEntry
import com.neoutils.finsight.feature.accounts.impl.AccountsEntryImpl
import com.neoutils.finsight.ui.modal.accountForm.AccountFormViewModel
import com.neoutils.finsight.ui.modal.archiveAccount.ArchiveAccountViewModel
import com.neoutils.finsight.ui.modal.deleteAccount.DeleteAccountViewModel
import com.neoutils.finsight.ui.modal.editAccountBalance.EditAccountBalanceViewModel
import com.neoutils.finsight.ui.modal.transferBetweenAccounts.TransferBetweenAccountsViewModel
import com.neoutils.finsight.ui.screen.accounts.AccountsViewModel
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

    factory { EnsureDefaultAccountUseCase(repository = get()) }
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
        )
    }
    factory {
        AdjustBalanceUseCase(
            transactionRepository = get(),
            calculateBalanceUseCase = get(),
        )
    }
    factory { AdjustFinalBalanceUseCase(adjustBalanceUseCase = get()) }
    factory { AdjustOpeningBalanceUseCase(adjustBalanceUseCase = get()) }
    factory {
        TransferBetweenAccountsUseCase(
            transactionRepository = get(),
            accountRepository = get(),
        )
    }

    single<AccountsEntry> { AccountsEntryImpl() }

    viewModel {
        AccountsViewModel(
            accountRepository = get(),
            transactionRepository = get(),
            categoryRepository = get(),
            entryRepository = get(),
            initialAccountId = it.getOrNull(),
        )
    }
    viewModel {
        AccountFormViewModel(
            account = it.getOrNull(),
            validateAccountName = get(),
            createAccountUseCase = get(),
            updateAccountUseCase = get(),
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
            type = it.get(),
            account = it.get(),
            targetMonth = it.getOrNull() ?: Clock.System.now().toYearMonth(),
            adjustBalanceUseCase = get(),
            adjustFinalBalanceUseCase = get(),
            adjustOpeningBalanceUseCase = get(),
            calculateBalanceUseCase = get(),
            accountRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        TransferBetweenAccountsViewModel(
            initialSourceAccount = it.get(),
            transferBetweenAccountsUseCase = get(),
            accountRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
}
