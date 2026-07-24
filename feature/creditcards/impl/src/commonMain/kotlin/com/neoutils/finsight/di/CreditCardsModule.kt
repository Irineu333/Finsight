package com.neoutils.finsight.di

import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.repository.CreditCardRepository
import com.neoutils.finsight.database.repository.InstallmentRepository
import com.neoutils.finsight.database.repository.InvoiceRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.ledger.DimensionWriteGuard
import com.neoutils.finsight.domain.ledger.InstallmentRemovalReconciler
import com.neoutils.finsight.domain.ledger.InvoiceWriteGuard
import com.neoutils.finsight.domain.ledger.TransactionRemovalHook
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import com.neoutils.finsight.feature.creditcards.impl.CreditCardsEntryImpl
import com.neoutils.finsight.ui.modal.addInstallment.AddInstallmentViewModel
import com.neoutils.finsight.ui.modal.advancePayment.AdvancePaymentViewModel
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceViewModel
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormViewModel
import com.neoutils.finsight.ui.modal.archiveCreditCard.ArchiveCreditCardViewModel
import com.neoutils.finsight.ui.modal.deleteCreditCard.DeleteCreditCardViewModel
import com.neoutils.finsight.ui.modal.deleteFutureInvoice.DeleteFutureInvoiceViewModel
import com.neoutils.finsight.ui.modal.deleteInstallment.DeleteInstallmentViewModel
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceViewModel
import com.neoutils.finsight.ui.modal.payInvoice.PayInvoiceViewModel
import com.neoutils.finsight.ui.modal.reopenInvoice.ReopenInvoiceViewModel
import com.neoutils.finsight.ui.modal.viewCreditCard.ViewCreditCardViewModel
import com.neoutils.finsight.ui.screen.archived.ArchivedCreditCardsViewModel
import com.neoutils.finsight.ui.screen.creditCards.CreditCardsViewModel
import com.neoutils.finsight.ui.screen.installments.InstallmentsViewModel
import com.neoutils.finsight.ui.screen.invoiceTransactions.InvoiceTransactionsViewModel
import com.neoutils.finsight.util.CreditCardPeriod
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val creditCardsModule = module {
    includes(useCaseModules, mapperModule)

    factory { CreditCardPeriod(defaultDaysDifference = 8) }

    single<ICreditCardRepository> {
        CreditCardRepository(
            database = get(),
            dao = get<AppDatabase>().creditCardDao(),
            accountDao = get(),
            mapper = get(),
        )
    }
    single<IInvoiceRepository> {
        InvoiceRepository(
            database = get(),
            dao = get(),
            dimensionDao = get(),
            mapper = get(),
            creditCardRepository = get(),
        )
    }
    single<IInstallmentRepository> {
        InstallmentRepository(
            database = get(),
            installmentDao = get(),
        )
    }

    // The ledger asks whoever owns a dimension whether a write may touch it; for an
    // invoice's sub-ledger, that owner is here (design D11).
    single<DimensionWriteGuard> { InvoiceWriteGuard(invoiceRepository = get()) }

    // …and tells it, after a removal, what an installment has to correct about
    // itself. A second contract rather than a second form on the first (D11).
    single<TransactionRemovalHook> {
        InstallmentRemovalReconciler(installmentRepository = get(), installmentDao = get())
    }

    single<CreditCardsEntry> { CreditCardsEntryImpl() }

    viewModel {
        CreditCardsViewModel(
            entryRepository = get(),
            recurringRepository = get(),
            initialCreditCardId = it.getOrNull(),
            creditCardRepository = get(),
            transactionRepository = get(),
            invoiceRepository = get(),
            invoiceUiMapper = get(),
            categoryRepository = get(),
            installmentRepository = get(),
        )
    }
    viewModel {
        InstallmentsViewModel(
            installmentRepository = get(),
            transactionRepository = get(),
            categoryRepository = get(),
            invoiceRepository = get(),
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
            transactions = it.get(),
            categoryRepository = get(),
            deleteInstallmentUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        DeleteFutureInvoiceViewModel(
            invoice = it.get(),
            deleteFutureInvoiceUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        CreditCardFormViewModel(
            formatter = get(),
            creditCard = it.getOrNull(),
            addCreditCardUseCase = get(),
            updateCreditCardUseCase = get(),
            validateCreditCardName = get(),
            modalManager = get(),
            debounceManager = get(),
            creditCardPeriod = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        DeleteCreditCardViewModel(
            creditCard = it.get(),
            deleteCreditCardUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        ArchiveCreditCardViewModel(
            creditCard = it.get(),
            archiveCreditCardUseCase = get(),
            entryRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        EditInvoiceBalanceViewModel(
            initialInvoice = it.get(),
            adjustInvoiceUseCase = get(),
            calculateInvoiceUseCase = get(),
            invoiceRepository = get(),
            creditCardRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        PayInvoiceViewModel(
            invoiceId = it.get(),
            invoiceRepository = get(),
            payInvoicePaymentUseCase = get(),
            payInvoiceUseCase = get(),
            calculateInvoiceUseCase = get(),
            accountRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        CloseInvoiceViewModel(
            invoiceId = it.get(),
            closeInvoiceUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        AdvancePaymentViewModel(
            invoiceId = it.get(),
            advanceInvoicePaymentUseCase = get(),
            accountRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        ReopenInvoiceViewModel(
            invoiceId = it.get(),
            reopenInvoiceUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        ViewCreditCardViewModel(
            cardId = it.get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            unarchiveCreditCard = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        ArchivedCreditCardsViewModel(
            creditCardRepository = get(),
        )
    }
    viewModel {
        InvoiceTransactionsViewModel(
            installmentRepository = get(),
            creditCardId = it.get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            transactionRepository = get(),
            categoryRepository = get(),
            entryRepository = get(),
            recurringRepository = get(),
            unarchiveCreditCard = get(),
            crashlytics = get(),
        )
    }
}
