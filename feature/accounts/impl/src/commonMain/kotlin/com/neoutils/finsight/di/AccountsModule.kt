package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.AccountMapper
import com.neoutils.finsight.database.repository.AccountRepository
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCase
import com.neoutils.finsight.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.IEnsureDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.SetDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.UpdateAccountUseCase
import com.neoutils.finsight.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finsight.ui.modal.accountForm.AccountFormViewModel
import com.neoutils.finsight.ui.modal.deleteAccount.DeleteAccountViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val accountsModule = module {

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
}
