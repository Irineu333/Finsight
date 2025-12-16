package com.neoutils.finance.di

import com.neoutils.finance.domain.usecase.AddCreditCardUseCase
import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustCreditCardBillUseCase
import com.neoutils.finance.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustInitialBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finance.domain.usecase.CalculateCreditCardBillUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finance.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finance.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finance.domain.usecase.GetCategoriesUseCase
import com.neoutils.finance.domain.usecase.GetCreditCardsUseCase
import com.neoutils.finance.domain.usecase.GetInvoiceForTransactionUseCase
import com.neoutils.finance.domain.usecase.GetOrCreateCurrentInvoiceUseCase
import com.neoutils.finance.domain.usecase.PayCreditCardBillUseCase
import com.neoutils.finance.domain.usecase.PayInvoiceUseCase
import com.neoutils.finance.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finance.domain.usecase.UpdateCreditCardUseCase
import org.koin.dsl.module

val useCaseModules = module {
    factory { AdjustBalanceUseCase(get(), get()) }
    factory { AdjustCreditCardBillUseCase(get(), get(), get(), get()) }
    factory { AdjustFinalBalanceUseCase(get()) }
    factory { AdjustInitialBalanceUseCase(get()) }
    factory { CalculateBalanceUseCase(get()) }
    factory { CalculateCreditCardBillUseCase() }
    factory { CalculateTransactionStatsUseCase() }
    factory { CalculateCategorySpendingUseCase() }
    factory { GetCategoriesUseCase(get()) }
    factory { GetCreditCardsUseCase(get()) }
    factory { AddCreditCardUseCase(get()) }
    factory { UpdateCreditCardUseCase(get()) }
    factory { DeleteCreditCardUseCase(get()) }
    factory { PayCreditCardBillUseCase(get(), get(), get(), get()) }
    factory { CreateInvoiceUseCase(get()) }
    factory { GetInvoiceForTransactionUseCase(get()) }
    factory { CloseInvoiceUseCase(get(), get(), get()) }
    factory { PayInvoiceUseCase(get()) }
    factory { GetOrCreateCurrentInvoiceUseCase(get(), get()) }
    factory { ReopenInvoiceUseCase(get()) }
}