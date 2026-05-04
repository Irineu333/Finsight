package com.neoutils.finsight.feature.installments.di

import com.neoutils.finsight.feature.installments.repository.InstallmentRepository
import com.neoutils.finsight.feature.installments.repository.IInstallmentRepository
import com.neoutils.finsight.feature.installments.usecase.AddInstallmentUseCase
import com.neoutils.finsight.feature.installments.usecase.IAddInstallmentUseCase
import com.neoutils.finsight.feature.installments.mapper.InstallmentUiMapper
import com.neoutils.finsight.feature.installments.modal.addInstallment.AddInstallmentViewModel
import com.neoutils.finsight.feature.installments.modal.deleteInstallment.DeleteInstallmentViewModel
import com.neoutils.finsight.feature.installments.screen.InstallmentsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val installmentsModule = module {

    factory { InstallmentUiMapper() }

    single<IInstallmentRepository> {
        InstallmentRepository(
            installmentDao = get(),
        )
    }

    factory<IAddInstallmentUseCase> {
        AddInstallmentUseCase(
            operationRepository = get(),
            installmentRepository = get(),
            invoiceRepository = get(),
            buildTransactionUseCase = get(),
            getOrCreateInvoiceForMonthUseCase = get(),
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
            invoiceRepository = get(),
            categoryRepository = get(),
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
