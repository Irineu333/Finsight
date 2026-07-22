package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionInstallment
import com.neoutils.finsight.domain.model.TransactionLabel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The edit gate of [ViewTransactionUiState.Content] is derived from the ledger
 * entries gate by gate (design D2 / spec "Editabilidade derivada"): not an
 * adjustment (label), exactly one monetary leg (entry types), no installment.
 * Each test isolates one gate so a green result cannot come from another gate.
 */
class ViewTransactionGatesTest {

    private val date = LocalDate(2026, 1, 1)

    private fun account(type: AccountType, isArchived: Boolean = false) =
        Account(name = type.name, type = type, isArchived = isArchived)

    private fun entry(type: AccountType, amount: Long, isArchived: Boolean = false) =
        Entry(account = account(type, isArchived), amount = amount)

    private fun content(
        entries: List<Entry>,
        installment: TransactionInstallment? = null,
    ) = ViewTransactionUiState.Content(
        transaction = Transaction(
            id = 1L,
            title = "Op",
            date = date,
            entries = entries,
            installmentId = installment?.id,
            installmentNumber = installment?.number,
        ),
        installment = installment,
    )

    @Test
    fun expenseInAccountIsEditable() {
        val content = content(
            entries = listOf(entry(AccountType.ASSET, -10_000), entry(AccountType.EXPENSE, 10_000)),
        )
        assertEquals(TransactionLabel.EXPENSE, content.label)
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
        assertEquals(TransactionLabel.ADJUSTMENT, content.label)
        assertFalse(content.isEditable)
    }

    @Test
    fun transferIsNotEditable_monetaryCountGate() {
        val content = content(
            entries = listOf(entry(AccountType.ASSET, -10_000), entry(AccountType.ASSET, 10_000)),
        )
        assertEquals(TransactionLabel.TRANSFER, content.label)
        assertFalse(content.isEditable)
    }

    @Test
    fun paymentIsNotEditable_monetaryCountGate() {
        val content = content(
            entries = listOf(entry(AccountType.ASSET, -10_000), entry(AccountType.LIABILITY, 10_000)),
        )
        assertEquals(TransactionLabel.PAYMENT, content.label)
        assertFalse(content.isEditable)
    }

    @Test
    fun expenseInArchivedAccountIsFrozen_changeGate() {
        // A monetary leg on an archived account freezes both actions: editing or
        // deleting would reopen a balance the archive required to be zero.
        val content = content(
            entries = listOf(entry(AccountType.ASSET, -10_000, isArchived = true), entry(AccountType.EXPENSE, 10_000)),
        )
        assertFalse(content.isChangeable)
        assertFalse(content.isEditable)
        assertFalse(content.isRemovable)
    }

    @Test
    fun purchaseOnArchivedCardIsFrozen_changeGate() {
        val content = content(
            entries = listOf(entry(AccountType.LIABILITY, -10_000, isArchived = true), entry(AccountType.EXPENSE, 10_000)),
        )
        assertFalse(content.isChangeable)
        assertFalse(content.isRemovable)
    }

    @Test
    fun expenseInArchivedCategoryStaysChangeable() {
        // A category is not monetary — archiving one strands nothing — so it freezes
        // neither action. Only the account/card facades gate here.
        val content = content(
            entries = listOf(entry(AccountType.ASSET, -10_000), entry(AccountType.EXPENSE, 10_000, isArchived = true)),
        )
        assertTrue(content.isChangeable)
        assertTrue(content.isEditable)
        assertTrue(content.isRemovable)
    }

    @Test
    fun installmentIsNotEditable_installmentGate() {
        val content = content(
            entries = listOf(entry(AccountType.LIABILITY, -10_000), entry(AccountType.EXPENSE, 10_000)),
            installment = TransactionInstallment(instance = Installment(count = 3, totalAmount = 300.0), number = 1),
        )
        // Passes every other gate — only the installment gate closes it.
        assertFalse(content.isEditable)
    }
}
