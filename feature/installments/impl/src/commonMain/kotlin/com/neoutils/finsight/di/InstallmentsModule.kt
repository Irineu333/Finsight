package com.neoutils.finsight.di

import com.neoutils.finsight.database.repository.InstallmentRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.ui.mapper.InstallmentUiMapper
import com.neoutils.finsight.ui.modal.addInstallment.AddInstallmentViewModel
import com.neoutils.finsight.ui.modal.deleteInstallment.DeleteInstallmentViewModel
import com.neoutils.finsight.ui.screen.installments.InstallmentsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val installmentsModule = module {

    factory { InstallmentUiMapper() }

    single<IInstallmentRepository> {
        InstallmentRepository(
            installmentDao = get(),
        )
    }

    factory {
        AddInstallmentUseCase(
            operationRepository = get(),
            installmentRepository = get(),
            invoiceRepository = get(),
            buildTransactionUseCase = get(),
            getOrCreateInvoiceForMonthUseCase = get(),
        )
    }

    viewModel {
        InstallmentsViewModel(
            installmentRepository = get(),
            operationRepository = get(),
            installmentUiMapper = get(),
        )
    }

    viewModel {
        AddInstallmentViewModel(
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            addInstallmentUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        DeleteInstallmentViewModel(
            installment = it.get(),
            operations = it.get(),
            operationRepository = get(),
            installmentRepository = get(),
            modalManager = get(),
            analytics = get(),
        )
    }
}
