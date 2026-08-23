package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_card_balance_adjustment
import com.neoutils.finsight.resources.transaction_card_invoice_adjustment
import com.neoutils.finsight.resources.transaction_card_payment
import com.neoutils.finsight.resources.transaction_card_transfer
import com.neoutils.finsight.resources.view_transaction_title_transfer
import com.neoutils.finsight.util.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OperationNameTest {

    private fun category(name: String) = Category(
        id = 1,
        name = name,
        icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE,
        createdAt = 0L,
        dimensionId = 1,
    )

    @Test
    fun theTitleWinsOverTheCategoryAndOverTheForm() {
        assertEquals("Café", displayTitleOrNull("Café", category("Mercado")))
        assertEquals(
            UiText.Raw("Café"),
            operationName("Café", TransactionLabel.EXPENSE, isCardTarget = false),
        )
    }

    @Test
    fun theCategoryNamesAnOperationWithoutATitle() {
        assertEquals("Mercado", displayTitleOrNull(title = null, category = category("Mercado")))
        assertEquals("Mercado", displayTitleOrNull(title = "  ", category = category("Mercado")))
    }

    @Test
    fun everyNatureHasAFormToBeNamedBy() {
        // Total, so a list row and a document line always have something to print: what
        // an operation *is* is derived from the entries, and is never absent.
        TransactionLabel.entries.forEach { label ->
            listOf(false, true).forEach { isCardTarget ->
                val name = operationName(displayTitle = null, label = label, isCardTarget = isCardTarget)
                assertEquals(UiText.Res::class, name::class, "$label / card=$isCardTarget")
            }
        }
    }

    @Test
    fun anAdjustmentIsNamedAfterTheTargetItCorrects() {
        assertEquals(
            UiText.Res(Res.string.transaction_card_balance_adjustment),
            operationName(null, TransactionLabel.ADJUSTMENT, isCardTarget = false),
        )
        assertEquals(
            UiText.Res(Res.string.transaction_card_invoice_adjustment),
            operationName(null, TransactionLabel.ADJUSTMENT, isCardTarget = true),
        )
    }

    @Test
    fun besideItsNatureATransferSaysWhereItWent() {
        // Named on its own it repeats the nature; beside it, it completes the sentence.
        assertEquals(
            UiText.Res(Res.string.transaction_card_transfer),
            operationName(null, TransactionLabel.TRANSFER, isCardTarget = false),
        )
        assertEquals(
            UiText.Res(Res.string.view_transaction_title_transfer),
            operationNameBesideNature(null, TransactionLabel.TRANSFER, isCardTarget = false),
        )
    }

    @Test
    fun besideItsNatureAnExpenseAndAnIncomeHaveNothingToAdd() {
        assertNull(operationNameBesideNature(null, TransactionLabel.EXPENSE, isCardTarget = false))
        assertNull(operationNameBesideNature(null, TransactionLabel.INCOME, isCardTarget = false))
    }

    @Test
    fun besideItsNatureAPaymentAndAnAdjustmentKeepTheirOwnName() {
        assertEquals(
            UiText.Res(Res.string.transaction_card_payment),
            operationNameBesideNature(null, TransactionLabel.PAYMENT, isCardTarget = true),
        )
        assertEquals(
            UiText.Res(Res.string.transaction_card_invoice_adjustment),
            operationNameBesideNature(null, TransactionLabel.ADJUSTMENT, isCardTarget = true),
        )
    }

    @Test
    fun theResolvableSetCoversEveryCellOfTheTable() {
        // What the exported document relies on: it resolves ahead of time, and a cell
        // the set forgot would be a missing key at print time.
        val names = operationFormNames()
        TransactionLabel.entries.forEach { label ->
            listOf(false, true).forEach { isCardTarget ->
                val name = operationName(null, label, isCardTarget) as UiText.Res
                assertEquals(true, name.res in names, "$label / card=$isCardTarget")
            }
        }
    }
}
