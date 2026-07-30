@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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

    private val today = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

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
            )
        )
    }
}
