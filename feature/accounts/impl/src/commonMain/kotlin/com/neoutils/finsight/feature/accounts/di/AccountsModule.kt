package com.neoutils.finsight.feature.accounts.di

import com.neoutils.finsight.feature.accounts.mapper.AccountMapper
import com.neoutils.finsight.feature.accounts.repository.AccountRepository
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.accounts.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.feature.accounts.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finsight.feature.accounts.usecase.AdjustInitialBalanceUseCase
import com.neoutils.finsight.feature.accounts.usecase.CreateAccountUseCase
import com.neoutils.finsight.feature.accounts.usecase.DeleteAccountUseCase
import com.neoutils.finsight.feature.accounts.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.feature.accounts.usecase.IEnsureDefaultAccountUseCase
import com.neoutils.finsight.feature.accounts.usecase.SetDefaultAccountUseCase
import com.neoutils.finsight.feature.accounts.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.feature.accounts.usecase.UpdateAccountUseCase
import com.neoutils.finsight.feature.accounts.usecase.ValidateAccountNameUseCase
import com.neoutils.finsight.feature.accounts.modal.accountForm.AccountFormModalEntry
import com.neoutils.finsight.feature.accounts.entryPoint.AccountFormModalEntryImpl
import com.neoutils.finsight.feature.accounts.modal.accountForm.AccountFormViewModel
import com.neoutils.finsight.feature.accounts.modal.deleteAccount.DeleteAccountViewModel
import com.neoutils.finsight.feature.accounts.modal.editAccountBalance.EditAccountBalanceViewModel
import com.neoutils.finsight.feature.accounts.modal.transferBetweenAccounts.TransferBetweenAccountsViewModel
import com.neoutils.finsight.feature.accounts.screen.AccountsViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.neoutils.finsight.core.utils.extension.toYearMonth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val accountsModule = module {

    single<AccountFormModalEntry> { AccountFormModalEntryImpl() }

    single { AccountMapper() }

    single<IAccountRepository> {
        AccountRepository(
            dao = get(),
            mapper = get(),
        )
    }

    factory {
        ValidateAccountNameUseCase(
            repository = get(),
        )
    }

    factory {
        SetDefaultAccountUseCase(
            repository = get(),
        )
    }

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

    factory {
        DeleteAccountUseCase(
            repository = get(),
        )
    }

    factory<IEnsureDefaultAccountUseCase> {
        EnsureDefaultAccountUseCase(
            repository = get(),
        )
    }

    factory {
        EnsureDefaultAccountUseCase(
            repository = get(),
        )
    }

    factory {
        AdjustBalanceUseCase(
            repository = get(),
            operationRepository = get(),
            calculateBalanceUseCase = get(),
        )
    }

    factory {
        AdjustFinalBalanceUseCase(
            adjustBalanceUseCase = get(),
        )
    }

    factory {
        AdjustInitialBalanceUseCase(
            adjustBalanceUseCase = get(),
        )
    }

    factory {
        TransferBetweenAccountsUseCase(
            operationRepository = get(),
            accountRepository = get(),
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

    @OptIn(ExperimentalTime::class)
    viewModel {
        EditAccountBalanceViewModel(
            type = it.get(),
            account = it.get(),
            targetMonth = it.getOrNull() ?: Clock.System.now().toYearMonth(),
            adjustBalanceUseCase = get(),
            adjustFinalBalanceUseCase = get(),
            adjustInitialBalanceUseCase = get(),
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

    viewModel {
        AccountsViewModel(
            accountRepository = get(),
            operationRepository = get(),
            categoryRepository = get(),
            operationUiMapper = get(),
            initialAccountId = it.getOrNull(),
        )
    }
}
