package com.neoutils.finsight.mcp.contract

import com.neoutils.finsight.domain.error.TransferError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolOutcomeTest {

    @Test
    fun `a refusal by a rule of the domain is marked as a tool execution error`() {
        val outcome = ToolOutcome.Failed(
            ToolError.domainRule("TRANSFER_SAME_ACCOUNT", TransferError.SameAccount.message),
        )

        assertTrue(outcome.isError)
        assertTrue(outcome.structuredContent["isError"]!!.jsonPrimitive.content.toBoolean())

        // The refusal travels inside the structured content, under the declared schema —
        // not as a plain result with an error object nobody is told to look for.
        val error = outcome.structuredContent["error"]!!.jsonObject
        assertEquals("DOMAIN_RULE", error["category"]!!.jsonPrimitive.content)
        assertEquals("TRANSFER_SAME_ACCOUNT", error["code"]!!.jsonPrimitive.content)
        assertNull(outcome.structuredContent["result"])
    }

    @Test
    fun `the message is the English one the domain carries`() {
        val error = ToolError.domainRule("TRANSFER_SAME_ACCOUNT", TransferError.SameAccount.message)

        assertEquals("Source account must be different from destination account.", error.message)
    }

    @Test
    fun `a rule of the domain is never retryable, an unavailability always is`() {
        assertFalse(ToolError.domainRule("CLOSED_INVOICE", "Invoice is closed").isRetryable)
        assertFalse(ToolError.notFound("NO_CATEGORY", "Category not found").isRetryable)
        assertFalse(ToolError.invalidInput("BAD_LIMIT", "Limit is not a number").isRetryable)
        assertFalse(ToolError.conflict("KEY_REUSED", "Idempotency key reused").isRetryable)
        assertTrue(ToolError.unavailable("RATE_LIMITED", "Too many calls").isRetryable)
        assertTrue(ToolError.internal("UNEXPECTED", "Unexpected failure").isRetryable)
    }

    @Test
    fun `an error cannot promise a retry its class forbids`() {
        assertFailsWith<IllegalArgumentException> {
            ToolError(
                category = ToolErrorCategory.DOMAIN_RULE,
                code = "CLOSED_INVOICE",
                message = "Invoice is closed",
                isRetryable = true,
            )
        }
    }

    @Test
    fun `a code is stable and enumerable, never prose`() {
        assertFailsWith<IllegalArgumentException> {
            ToolError.domainRule("closed invoice", "Invoice is closed")
        }
        assertFailsWith<IllegalArgumentException> {
            ToolError.domainRule("CLOSED_INVOICE", "  ")
        }
    }

    @Test
    fun `a missing rate is a warning on a successful result, not an error`() {
        val gap = ConsolidatedMoney.Unavailable(
            reason = ConsolidationGap.MISSING_RATE,
            message = "${ConsolidationGap.MISSING_RATE.message}: USD",
        )

        val outcome = ToolOutcome.Ok(
            result = buildJsonObject { put("netWorth", "per currency") },
            warnings = listOf(gap.asWarning()),
        )

        assertFalse(outcome.isError)
        val warnings = outcome.structuredContent["warnings"]!!.jsonArray
        assertEquals(1, warnings.size)
        assertEquals(
            ToolWarningCode.MISSING_EXCHANGE_RATE.name,
            warnings.single().jsonObject["code"]!!.jsonPrimitive.content,
        )
        // The figure itself is still in the result: a partial answer, not a failure.
        assertTrue(outcome.structuredContent["result"] is JsonObject)
    }

    @Test
    fun `a warning is a field, not prose`() {
        val warning = ToolWarning(
            code = ToolWarningCode.PROBABLE_DUPLICATE,
            message = "An existing transaction matches date, amount, account and description",
            details = mapOf("itemIndex" to "3", "transactionId" to "417"),
        )

        val outcome = ToolOutcome.Ok(buildJsonObject { }, warnings = listOf(warning))
        val encoded = outcome.structuredContent["warnings"]!!.jsonArray.single().jsonObject

        assertEquals("PROBABLE_DUPLICATE", encoded["code"]!!.jsonPrimitive.content)
        assertEquals("3", encoded["details"]!!.jsonObject["itemIndex"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the output schema enumerates the codes the tool can emit`() {
        val schema = toolOutcomeSchema(
            resultSchema = buildJsonObject { put("type", "object") },
            errorCodes = setOf("CLOSED_INVOICE", "CATEGORY_NOT_FOUND"),
        )

        val codes = schema["properties"]!!.jsonObject["error"]!!.jsonObject["properties"]!!
            .jsonObject["code"]!!.jsonObject["enum"]!!.jsonArray
            .map { it.jsonPrimitive.content }

        assertEquals(listOf("CATEGORY_NOT_FOUND", "CLOSED_INVOICE"), codes)
    }

    @Test
    fun `a tool that enumerates no code is refused`() {
        assertFailsWith<IllegalArgumentException> {
            toolOutcomeSchema(buildJsonObject { put("type", "object") }, errorCodes = emptySet())
        }
    }
}
