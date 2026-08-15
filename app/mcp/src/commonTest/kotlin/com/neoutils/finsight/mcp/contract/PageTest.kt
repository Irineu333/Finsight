package com.neoutils.finsight.mcp.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageTest {

    @Test
    fun `a limit above the ceiling is refused, and the refusal names the ceiling`() {
        val refused = assertIs<PageLimit.Refused>(resolvePageLimit(500))

        assertEquals(ResponseLimits.CODE_PAGE_LIMIT_ABOVE_CEILING, refused.error.code)
        assertEquals(ToolErrorCategory.INVALID_INPUT, refused.error.category)
        assertTrue(
            refused.error.message.contains(ResponseLimits.MAX_PAGE_SIZE.toString()),
            refused.error.message,
        )
    }

    @Test
    fun `a limit that fits is served as asked, and no limit takes the default`() {
        assertEquals(PageLimit.Accepted(120, wasAssumed = false), resolvePageLimit(120))
        assertEquals(
            PageLimit.Accepted(ResponseLimits.DEFAULT_PAGE_SIZE, wasAssumed = true),
            resolvePageLimit(null),
        )
    }

    @Test
    fun `a limit below one is refused`() {
        val refused = assertIs<PageLimit.Refused>(resolvePageLimit(0))

        assertEquals(ResponseLimits.CODE_PAGE_LIMIT_NOT_POSITIVE, refused.error.code)
    }

    @Test
    fun `a page reports the total that satisfies the filter, not the page`() {
        val page = Page(items = listOf("a", "b"), totalMatching = 317, nextCursor = Cursor.of("id:2"))

        assertEquals(2, page.items.size)
        assertEquals(317, page.totalMatching)
    }

    @Test
    fun `a page cannot claim fewer matches than it carries`() {
        assertFailsWith<IllegalArgumentException> {
            Page(items = listOf("a", "b"), totalMatching = 1)
        }
    }

    @Test
    fun `the last page has no cursor`() {
        assertNull(Page(items = listOf("a"), totalMatching = 1).nextCursor)
    }

    @Test
    fun `a cursor is opaque and never a numeric offset`() {
        val cursor = Cursor.of("id:42")

        assertTrue(cursor.value != "42")
        assertNull(cursor.value.toIntOrNull())
        assertEquals("id:42", cursor.decode())
    }

    @Test
    fun `a response within the declared size is served`() {
        assertNull(refuseIfOversized(ResponseLimits.MAX_RESPONSE_BYTES, "request a shorter period"))
    }

    @Test
    fun `a response above the declared size is refused with guidance`() {
        val error = refuseIfOversized(
            bytes = ResponseLimits.MAX_RESPONSE_BYTES + 1,
            howToNarrow = "request a shorter period, or group by month instead of by day",
        )

        assertEquals(ResponseLimits.CODE_RESPONSE_TOO_LARGE, error?.code)
        assertTrue(error!!.message.contains("group by month"), error.message)
        assertTrue(error.message.contains(ResponseLimits.MAX_RESPONSE_BYTES.toString()), error.message)
    }

    @Test
    fun `a size refusal without guidance is itself refused`() {
        assertFailsWith<IllegalArgumentException> {
            refuseIfOversized(ResponseLimits.MAX_RESPONSE_BYTES + 1, howToNarrow = " ")
        }
    }
}
