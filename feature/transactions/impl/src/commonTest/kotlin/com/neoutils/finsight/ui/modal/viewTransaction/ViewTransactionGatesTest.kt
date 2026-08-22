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
 * The edit gate of [ViewTransactionUiState.Content] is derived from the ledger entries
 * (spec "Editabilidade derivada"): the gates that hold for every operation — archived
 * leg, installment — and then a decision **by label**. Each test isolates one gate so a
 * green result cannot come from another gate.
 *
 * The count of monetary legs survives only where it means something: an expense or an
 * income with two of them is not a shape the transaction form writes. It says nothing
 * about the transfer, which is admitted by name, nor about the card payment, which is
 * refused by name.
 */
class ViewTransactionGatesTest {

    private val date = LocalDate(2026, 1, 1)

    private fun account(
        type: AccountType,
        isArchived: Boolean = false,
        id: Long = 0,
        currency: String = "BRL",
    ) = Account(id = id, name = "$type$id", type = type, isArchived = isArchived, currency = currency)

    private fun entry(
        type: AccountType,
        amount: Long,
        isArchived: Boolean = false,
        id: Long = 0,
        currency: String = "BRL",
    ) = Entry(account = account(type, isArchived, id, currency), amount = amount)

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
    fun transferIsEditable_labelGate() {
        val content = content(
            entries = listOf(
                entry(AccountType.ASSET, -10_000, id = 1),
                entry(AccountType.ASSET, 10_000, id = 2),
            ),
        )
        assertEquals(TransactionLabel.TRANSFER, content.label)
        assertEquals(2, content.transaction.monetaryEntries.size)
        assertTrue(content.isEditable, "two monetary legs, and the transfer form states both")
    }

    @Test
    fun crossCurrencyTransferIsEditable_sameGate() {
        // Four legs, two of them conversion. They are not monetary and do not change
        // the label, so this passes the very same gate with no branch of its own.
        val content = content(
            entries = listOf(
                entry(AccountType.ASSET, -55_000, id = 1),
                entry(AccountType.CONVERSION, 55_000, id = 10),
                entry(AccountType.CONVERSION, -10_000, id = 11, currency = "USD"),
                entry(AccountType.ASSET, 10_000, id = 2, currency = "USD"),
            ),
        )
        assertEquals(TransactionLabel.TRANSFER, content.label)
        assertTrue(content.isEditable)
    }

    @Test
    fun transferWithArchivedLegIsFrozen_changeGate() {
        // The archived-account gate precedes the decision by label, and one archived
        // end is enough: retargeting the operation would hand a balance back to an
        // account that accepts no entries and appears in no selector.
        val content = content(
            entries = listOf(
                entry(AccountType.ASSET, -10_000, id = 1, isArchived = true),
                entry(AccountType.ASSET, 10_000, id = 2),
            ),
        )
        assertEquals(TransactionLabel.TRANSFER, content.label)
        assertFalse(content.isChangeable)
        assertFalse(content.isEditable)
        assertFalse(content.isRemovable)
    }

    @Test
    fun paymentIsNotEditable_byDeclaration() {
        // Out of scope by declaration, and no longer by leg count: a payment has the
        // same two monetary legs a transfer has, and the transfer is now editable.
        val content = content(
            entries = listOf(
                entry(AccountType.ASSET, -10_000, id = 1),
                entry(AccountType.LIABILITY, 10_000, id = 2),
            ),
        )
        assertEquals(TransactionLabel.PAYMENT, content.label)
        assertEquals(2, content.transaction.monetaryEntries.size)
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
