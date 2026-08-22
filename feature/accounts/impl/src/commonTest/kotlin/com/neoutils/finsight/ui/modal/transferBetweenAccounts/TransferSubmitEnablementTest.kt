package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The submit button covering the **second** field — the validation change design D26
 * calls the easiest to forget.
 *
 * It is what keeps the write boundary's same-sign refusal unreachable by any path a user
 * can walk: with one field "leaves" and the other "arrives", the residues oppose each
 * other by construction, so only a zero could reach that guard, and this refuses one
 * first.
 */
class TransferSubmitEnablementTest {

    // Fixed, and handed to the rule: what "today" is belongs to the caller, so the
    // assertion never depends on the day the suite happens to run.
    private val today = LocalDate(2026, 3, 14)

    private val date = dayMonthYear.format(today)

    private val nubank = Account(id = 1, name = "Nubank", currency = "BRL")
    private val chase = Account(id = 2, name = "Chase", currency = "USD")
    private val itau = Account(id = 3, name = "Itaú", currency = "BRL")

    @Test
    fun `a cross-currency transfer waits for what arrives`() {
        assertFalse(
            isValidTransfer(
                amount = "R$ 550,00",
                destinationAmount = "",
                isCrossCurrency = true,
                date = date,
                sourceAccount = nubank,
                destinationAccount = chase,
                today = today,
            )
        )
    }

    @Test
    fun `a second field of zero is no more submittable than an empty one`() {
        assertFalse(
            isValidTransfer(
                amount = "R$ 550,00",
                destinationAmount = "US$ 0,00",
                isCrossCurrency = true,
                date = date,
                sourceAccount = nubank,
                destinationAccount = chase,
                today = today,
            )
        )
    }

    @Test
    fun `both ends stated is submittable`() {
        assertTrue(
            isValidTransfer(
                amount = "R$ 550,00",
                destinationAmount = "US$ 100,00",
                isCrossCurrency = true,
                date = date,
                sourceAccount = nubank,
                destinationAccount = chase,
                today = today,
            )
        )
    }

    /** The single-currency form is identical to what it was: there is no second field. */
    @Test
    fun `a same-currency transfer never waits for a second field`() {
        assertTrue(
            isValidTransfer(
                amount = "R$ 550,00",
                destinationAmount = "",
                isCrossCurrency = false,
                date = date,
                sourceAccount = nubank,
                destinationAccount = itau,
                today = today,
            )
        )
    }

    // --- The two refusals the button reaches before the write does ---
    //
    // ValidateTransferUseCase owns what makes a transfer admissible, and refuses both of
    // these when a submission arrives. The button refuses them earlier, so the user never
    // reaches an error dialog for something the form could see: a disabled button is the
    // form declining to offer what it can tell will not be accepted, which is a decision
    // about affordance and not a second statement of the rule.

    @Test
    fun `the same account on both ends is not submittable`() {
        assertFalse(
            isValidTransfer(
                amount = "R$ 550,00",
                destinationAmount = "",
                isCrossCurrency = false,
                date = date,
                sourceAccount = nubank,
                destinationAccount = nubank,
                today = today,
            )
        )
    }

    @Test
    fun `a date after the caller's today is not submittable`() {
        assertFalse(
            isValidTransfer(
                amount = "R$ 550,00",
                destinationAmount = "",
                isCrossCurrency = false,
                date = dayMonthYear.format(today.plus(1, DateTimeUnit.DAY)),
                sourceAccount = nubank,
                destinationAccount = itau,
                today = today,
            )
        )
    }
}
