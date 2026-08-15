package com.neoutils.finsight.mcp.contract

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssumedDefaultsTest {

    private val today = LocalDate(2026, 8, 14)
    private val zone = TimeZone.of("America/Sao_Paulo")

    @Test
    fun `a reference date the call did not carry is echoed as assumed`() {
        val defaults = AssumedDefaults.resolve(today = today, timeZone = zone)

        assertEquals(today, defaults.referenceDate.value)
        assertTrue(defaults.referenceDate.wasAssumed)
    }

    @Test
    fun `a reference date the call carried is echoed as its own`() {
        val asked = LocalDate(2026, 3, 31)
        val defaults = AssumedDefaults.resolve(today, zone, referenceDate = asked)

        assertEquals(asked, defaults.referenceDate.value)
        assertTrue(!defaults.referenceDate.wasAssumed)
    }

    @Test
    fun `archived records are out by omission, and the scope applied is in the answer`() {
        val defaults = AssumedDefaults.resolve(today, zone)

        assertEquals(ArchivedScope.EXCLUDED, defaults.archived.value)
        assertTrue(defaults.archived.wasAssumed)
    }

    @Test
    fun `a scope the call asked for is echoed as its own`() {
        val defaults = AssumedDefaults.resolve(today, zone, archived = ArchivedScope.INCLUDED)

        assertEquals(ArchivedScope.INCLUDED, defaults.archived.value)
        assertTrue(!defaults.archived.wasAssumed)
    }

    @Test
    fun `the period is echoed when there is one, and absent when there is none`() {
        val period = CivilDateRange(LocalDate(2026, 7, 1), LocalDate(2026, 7, 31))

        assertEquals(period, AssumedDefaults.resolve(today, zone, period = period).period?.value)
        assertNull(AssumedDefaults.resolve(today, zone).period)
    }

    @Test
    fun `dates are civil, in the user's time zone, and the zone is named`() {
        assertEquals("America/Sao_Paulo", AssumedDefaults.resolve(today, zone).timeZone)
    }

    @Test
    fun `a period that ends before it starts is refused`() {
        assertFailsWith<IllegalArgumentException> {
            CivilDateRange(LocalDate(2026, 7, 31), LocalDate(2026, 7, 1))
        }
    }

    @Test
    fun `an ISO civil date is accepted`() {
        val parsed = assertIs<CivilDate.Accepted>(parseCivilDate("2026-07-31"))

        assertEquals(LocalDate(2026, 7, 31), parsed.date)
    }

    @Test
    fun `natural language is not interpreted`() {
        listOf("today", "last month", "ontem", "next friday", "07/31/2026").forEach { raw ->
            val refused = assertIs<CivilDate.Refused>(parseCivilDate(raw), raw)
            assertEquals(AssumedDefaults.CODE_NOT_A_CIVIL_DATE, refused.error.code)
            assertEquals(ToolErrorCategory.INVALID_INPUT, refused.error.category)
        }
    }

    @Test
    fun `a date that is not a date is refused, not guessed`() {
        assertIs<CivilDate.Refused>(parseCivilDate("2026-13-01"))
        assertIs<CivilDate.Refused>(parseCivilDate("2026-02-30"))
    }
}
