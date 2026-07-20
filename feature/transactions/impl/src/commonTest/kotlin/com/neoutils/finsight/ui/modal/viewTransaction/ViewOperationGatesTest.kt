package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationInstallment
import com.neoutils.finsight.domain.model.OperationLabel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The edit gate of [ViewOperationUiState.Content] is derived from the ledger
 * entries gate by gate (design D2 / spec "Editabilidade derivada"): not an
 * adjustment (label), exactly one monetary leg (entry types), no installment.
 * Each test isolates one gate so a green result cannot come from another gate.
 */
class ViewOperationGatesTest {

    private val date = LocalDate(2026, 1, 1)

    private fun account(type: AccountType) = Account(name = type.name, type = type)

    private fun entry(type: AccountType, amount: Long) = Entry(account = account(type), amount = amount)

    private fun content(
        entries: List<Entry>,
        installment: OperationInstallment? = null,
    ) = ViewOperationUiState.Content(
        operation = Operation(
            id = 1L,
            title = "Op",
            date = date,
            entries = entries,
            installment = installment,
        ),
    )

    @Test
    fun expenseInAccountIsEditable() {
        val content = content(
            entries = listOf(entry(AccountType.ASSET, -10_000), entry(AccountType.EXPENSE, 10_000)),
        )
        assertEquals(OperationLabel.EXPENSE, content.label)
        assertTrue(content.isEditable)
    }

    @Test
    fun cardPurchaseIsEditable() {
        val content = content(
            entries = listOf(entry(AccountType.LIABILITY, -10_000), entry(AccountType.EXPENSE, 10_000)),
        )
        assertTrue(content.isEditable)
    }

    @Test
    fun adjustmentIsNotEditable_labelGate() {
        val content = content(
            entries = listOf(entry(AccountType.ASSET, -10_000), entry(AccountType.EQUITY, 10_000)),
        )
        assertEquals(OperationLabel.ADJUSTMENT, content.label)
        assertFalse(content.isEditable)
    }

    @Test
    fun transferIsNotEditable_monetaryCountGate() {
        val content = content(
            entries = listOf(entry(AccountType.ASSET, -10_000), entry(AccountType.ASSET, 10_000)),
        )
        assertEquals(OperationLabel.TRANSFER, content.label)
        assertFalse(content.isEditable)
    }

    @Test
    fun paymentIsNotEditable_monetaryCountGate() {
        val content = content(
            entries = listOf(entry(AccountType.ASSET, -10_000), entry(AccountType.LIABILITY, 10_000)),
        )
        assertEquals(OperationLabel.PAYMENT, content.label)
        assertFalse(content.isEditable)
    }

    @Test
    fun installmentIsNotEditable_installmentGate() {
        val content = content(
            entries = listOf(entry(AccountType.LIABILITY, -10_000), entry(AccountType.EXPENSE, 10_000)),
            installment = OperationInstallment(instance = Installment(count = 3, totalAmount = 300.0), number = 1),
        )
        // Passes every other gate — only the installment gate closes it.
        assertFalse(content.isEditable)
    }
}
