package com.neoutils.finsight.domain.analytics

import com.neoutils.finsight.analytics.wireParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The backend cuts an over-long parameter in silence. These are the two cuts this module
 * makes first, so that what arrives is a value the codebase chose rather than one nobody
 * ever saw being made.
 */
class EventParamsTest {

    /** A dashboard layout: real keys, and more of them than one parameter can carry. */
    private val components = listOf(
        "total_balance",
        "balance_stats_overall",
        "balance_stats_concrete",
        "month_settlement",
        "balance_stats_credit_card",
        "accounts_overview",
        "credit_cards_pager",
        "spending_by_category",
        "income_by_category",
    )

    @Test
    fun `a list too long for one parameter is cut at an element boundary`() {
        val value = buildMap { putList("components", components) }.getValue("components")

        assertTrue(value.length <= MAX_PARAM_VALUE_LENGTH, "over the cap: ${value.length}")
        assertTrue(
            value.split(",").all { it in components },
            "cut mid-element, publishing a key that does not exist: $value",
        )
    }

    /** Without this, a cut list is indistinguishable from a short one. */
    @Test
    fun `the count states how many there were, not how many fit`() {
        val params = buildMap { putList("components", components) }

        assertEquals(components.size.toString(), params.getValue("components_count"))
        assertTrue(params.getValue("components").split(",").size < components.size)
    }

    @Test
    fun `a list that fits is sent whole`() {
        val sections = listOf("spending_by_category", "income_by_category", "transaction_list")

        val params = buildMap { putList("sections", sections) }

        assertEquals(sections.joinToString(","), params.getValue("sections"))
        assertEquals("3", params.getValue("sections_count"))
    }

    @Test
    fun `an empty list is a count of zero`() {
        val params = buildMap { putList("categories", emptyList()) }

        assertEquals("", params.getValue("categories"))
        assertEquals("0", params.getValue("categories_count"))
    }

    /**
     * Free text the user typed — a category name has no length limit anywhere in this
     * app, so the cap cannot be a call site's responsibility.
     */
    @Test
    fun `a value over the cap is cut before it reaches the wire`() {
        val event = object : Event("create_category", mapOf("name" to "a".repeat(250))) {}

        assertEquals(MAX_PARAM_VALUE_LENGTH, event.wireParams().getValue("name").length)
        assertEquals(250, event.params.getValue("name").length)
    }
}
