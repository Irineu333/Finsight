package com.neoutils.finsight.ui.modal.viewTransaction

import androidx.compose.runtime.Composable
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.feature.accounts.api.AccountsEntry
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.editTransaction.EditTransactionModal
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Which form corrects an operation follows from what the operation **is**.
 *
 * The two forms this feature does not own are reached through the public entry point of
 * the feature that does, and the test names those entry points and not the modals behind
 * them — which is the same boundary the screen is held to.
 */
class EditFormTest {

    private val date = LocalDate(2026, 1, 1)

    private fun entry(type: AccountType, amount: Long, id: Long = 0) = Entry(
        account = Account(id = id, name = "$type$id", type = type, currency = "BRL"),
        amount = amount,
    )

    private fun transaction(entries: List<Entry>) =
        Transaction(id = 1, title = null, date = date, entries = entries)

    private val accountsEntry = RecordingAccountsEntry()
    private val creditCardsEntry = RecordingCreditCardsEntry()

    private fun formFor(entries: List<Entry>) = editFormFor(
        transaction = transaction(entries),
        accountsEntry = accountsEntry,
        creditCardsEntry = creditCardsEntry,
    )

    @Test
    fun `a payment opens the payment form`() {
        val operation = transaction(
            listOf(
                entry(AccountType.ASSET, -10_000, id = 1),
                entry(AccountType.LIABILITY, 10_000, id = 2),
            )
        )
        assertEquals(TransactionLabel.PAYMENT, operation.label)

        val form = editFormFor(operation, accountsEntry, creditCardsEntry)

        assertEquals(StubModal("edit-invoice-payment"), form)
        assertEquals(operation, creditCardsEntry.corrected)
    }

    @Test
    fun `a transfer opens the transfer form`() {
        val form = formFor(
            listOf(
                entry(AccountType.ASSET, -10_000, id = 1),
                entry(AccountType.ASSET, 10_000, id = 2),
            )
        )
        assertEquals(StubModal("edit-transfer"), form)
    }

    @Test
    fun `an expense opens the transaction form`() {
        val form = formFor(
            listOf(
                entry(AccountType.ASSET, -10_000, id = 1),
                entry(AccountType.EXPENSE, 10_000, id = 2),
            )
        )
        assertIs<EditTransactionModal>(form)
    }
}

private data class StubModal(val name: String) : Modal() {
    @Composable
    override fun Content() = Unit
}

private class RecordingAccountsEntry : AccountsEntry {
    override fun accountFormModal(account: Account?): Modal = StubModal("account-form")
    override fun editTransferModal(transaction: Transaction): Modal = StubModal("edit-transfer")
}

private class RecordingCreditCardsEntry : CreditCardsEntry {

    var corrected: Transaction? = null
        private set

    override fun editInvoicePaymentModal(transaction: Transaction): Modal {
        corrected = transaction
        return StubModal("edit-invoice-payment")
    }

    override fun creditCardFormModal(creditCard: CreditCard?): Modal = notUnderTest()
    override fun invoicePaymentModal(invoiceId: Long?): Modal = notUnderTest()
    override fun closeInvoiceModal(invoiceId: Long, closingDate: LocalDate): Modal = notUnderTest()
    override fun editInvoiceBalanceModal(invoice: com.neoutils.finsight.domain.model.Invoice): Modal = notUnderTest()
}

private fun notUnderTest(): Nothing = error("not part of the choice of form under test")
