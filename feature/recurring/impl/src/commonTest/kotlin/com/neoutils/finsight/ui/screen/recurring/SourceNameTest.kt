package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **What the row calls a source it cannot post to.**
 *
 * `hasUsableSource` is false for the archived source and for the removed one alike, and
 * the row's unusable branch used to answer both with the same sentence. Only one of the
 * two has nothing to be named: this is the distinction, and the row's whole job — telling
 * two like-labelled templates apart — rides on it.
 */
class SourceNameTest {

    private fun recurring(
        account: Account? = null,
        creditCard: CreditCard? = null,
    ) = Recurring(
        id = 1L,
        type = TransactionType.EXPENSE,
        amount = 100.0,
        title = "Aluguel",
        dayOfMonth = 5,
        category = null,
        account = account,
        creditCard = creditCard,
        createdAt = 0L,
    )

    private fun account(name: String, isArchived: Boolean = false) = Account(
        id = 1L,
        name = name,
        currency = "BRL",
        isArchived = isArchived,
    )

    private fun creditCard(name: String, isArchived: Boolean = false) = CreditCard(
        id = 1L,
        name = name,
        limit = 1_000.0,
        closingDay = 1,
        dueDay = 10,
        isArchived = isArchived,
    )

    @Test
    fun `an archived account is still named`() {
        val template = recurring(account = account("Banco A", isArchived = true))

        assertEquals("Banco A", template.sourceName())
    }

    @Test
    fun `an archived card is still named`() {
        val template = recurring(creditCard = creditCard("Nubank", isArchived = true))

        assertEquals("Nubank", template.sourceName())
    }

    /**
     * The only absence with nothing behind it: the foreign key is `SET_NULL`, so a removed
     * source leaves both sides null and the sentence is all the row has left to say.
     */
    @Test
    fun `a removed source has no name to give`() {
        assertNull(recurring().sourceName())
    }

    /** Two archived banks, one label: the names are what keep the rows apart. */
    @Test
    fun `two templates of the same label are told apart by their archived sources`() {
        val first = recurring(account = account("Banco A", isArchived = true))
        val second = recurring(account = account("Banco B", isArchived = true))

        assertEquals("Banco A", first.sourceName())
        assertEquals("Banco B", second.sourceName())
    }

    /** The card is the more specific source, and answers first. */
    @Test
    fun `a card outranks an account`() {
        val template = recurring(
            account = account("Banco A"),
            creditCard = creditCard("Nubank"),
        )

        assertEquals("Nubank", template.sourceName())
    }
}
