package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_card_payment
import com.neoutils.finsight.resources.view_transaction_title_balance_adjustment
import com.neoutils.finsight.resources.view_transaction_title_invoice_adjustment
import com.neoutils.finsight.resources.view_transaction_title_transfer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FallbackTitleTest {

    @Test
    fun anAccountAdjustmentIsNamedAfterTheBalanceItCorrects() {
        assertEquals(
            Res.string.view_transaction_title_balance_adjustment,
            fallbackTitleFor(TransactionLabel.ADJUSTMENT, isCardTarget = false),
        )
    }

    @Test
    fun aCardAdjustmentIsNamedAfterTheInvoiceItCorrects() {
        // The liability leg is the whole difference, and it is the ledger's fact —
        // the same one the card badge on the icon reads.
        assertEquals(
            Res.string.view_transaction_title_invoice_adjustment,
            fallbackTitleFor(TransactionLabel.ADJUSTMENT, isCardTarget = true),
        )
    }

    @Test
    fun aTransferAndAPaymentAreNamedAfterTheirForm() {
        assertEquals(
            Res.string.view_transaction_title_transfer,
            fallbackTitleFor(TransactionLabel.TRANSFER, isCardTarget = false),
        )
        assertEquals(
            Res.string.transaction_card_payment,
            fallbackTitleFor(TransactionLabel.PAYMENT, isCardTarget = true),
        )
    }

    @Test
    fun anExpenseAndAnIncomeHaveNoNameBeyondTheirNature() {
        // The line is omitted, not filled with a literal: naming an absence is the one
        // thing the header does not do.
        TransactionLabel.entries
            .filter { it == TransactionLabel.EXPENSE || it == TransactionLabel.INCOME }
            .forEach { label ->
                assertNull(fallbackTitleFor(label, isCardTarget = false))
                assertNull(fallbackTitleFor(label, isCardTarget = true))
            }
    }
}
