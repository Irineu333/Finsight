package com.neoutils.finsight.di

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal
import org.koin.dsl.module

/**
 * Bindings transitórios de Entry para features ainda não extraídas.
 * O shell enxerga os modais residentes no :composeApp e os expõe via a interface
 * da api. Quando o :feature:*:impl dono é extraído, o binding real migra para o
 * módulo Koin da feature e a entrada correspondente sai daqui.
 */
val transitionalEntriesModule = module {
    single<CreditCardsEntry> {
        object : CreditCardsEntry {
            override fun creditCardFormModal(creditCard: CreditCard?): Modal =
                CreditCardFormModal(creditCard)
        }
    }
}
