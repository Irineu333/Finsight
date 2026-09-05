package com.neoutils.finsight.mcp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The HTTP client this module holds only ever dials this machine.**
 *
 * The guarantee used to be structural and free: `RemoteSourceIsNeverReadTest` (`:app:shared`) held
 * that exactly one module in the app declared a Ktor client, and it was the exchange-rate source, so
 * "nothing else reaches the network" was a fact about the module graph. The bridge cost that: this
 * module now declares a client of its own, and the graph can no longer tell a client that dials
 * `127.0.0.1` from one that dials a host on the internet. Without something in its place, "the mcp
 * client does not leave the machine" is discipline rather than fact.
 *
 * This is that something, and it is the same kind of guard as
 * `RegistrationToolsGoThroughUseCasesTest`: it reads the sources, because what it asks about is what
 * is **declared** — nothing that runs can observe it, and code written tomorrow is covered the day
 * it is written, without anybody adding it to a list.
 *
 * Three things are asserted, and together they close the question. The client is **built in one
 * place**, so there is one address to check. Every address the module spells is **loopback**,
 * literally or through the constant the socket is bound to. And that constant **is** the loopback
 * address, so the indirection cannot be where a host slips in.
 */
class TheClientOfThisModuleNeverLeavesTheMachineTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val module = File(repoRoot, "feature/mcp/impl/src")

    /** Every production source of this module — what ships, never what a test states. */
    private val sources: List<File> = module.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filter { Regex("/src/[a-zA-Z]*Main/") in it.invariantPath() }
        .toList()

    // ------------------------------------------------------------------------------
    // The floor: everything below rests on the scan reaching what it claims to read
    // ------------------------------------------------------------------------------

    @Test
    fun `the scan reaches the production sources of this module`() {
        assertTrue(
            sources.size >= 50,
            "only ${sources.size} production sources were found under $module; the scan is no " +
                "longer reading the module it asserts about.",
        )
        assertTrue(
            addresses().isNotEmpty(),
            "the scan found no address at all in a module that binds one and dials one, so its " +
                "silence below means nothing.",
        )
    }

    /**
     * The scan has to **recognise a host that leaves the machine**, or its silence proves nothing.
     *
     * The sample is the shape the guard exists to catch, spelled the way it would really appear: a
     * client built against somebody else's server. Without this, a regex that stopped matching
     * would turn the assertions below green forever.
     */
    @Test
    fun `the scan recognises an address that leaves the machine`() {
        val forged = """
            private val engine = HttpClient(OkHttp)
            private const val WHERE = "https://api.example.com/v1/rates"
        """.trimIndent()

        assertEquals(listOf("api.example.com"), hostsIn(forged))
        assertTrue(hostsIn(forged).none { it in LOOPBACK }, "the forged host read as loopback")
    }

    // ------------------------------------------------------------------------------
    // The rules
    // ------------------------------------------------------------------------------

    /**
     * One place builds the client, so there is one address to be sure of.
     *
     * A second `HttpClient` somewhere in this module would be a second conversation, with its own
     * address and its own reasons, and the assertion below would be checking one of them.
     */
    @Test
    fun `only the bridge builds an http client`() {
        val builders = sources
            .filter { Regex("""\bHttpClient\s*\(""").containsMatchIn(it.readText().withoutComments()) }
            .map { it.relativeTo(repoRoot).invariantSeparatorsPath }
            .toSet()

        assertEquals(
            setOf(BRIDGE),
            builders,
            "The client of this module is built somewhere other than the bridge. The bridge dials " +
                "the server this same app is holding open, on loopback; a client built elsewhere " +
                "is a conversation nobody has reasoned about.\n" +
                (builders - setOf(BRIDGE)).joinToString("\n") { "  NEW: $it" },
        )
    }

    /**
     * Every address this module spells is on this machine.
     *
     * The bridge's own is built from the port the user persisted and [LOOPBACK_HOST]; the socket
     * binds the same constant and validates `Host` and `Origin` against the three spellings of it.
     * A fourth authority appearing here is either a request leaving the machine or a defence being
     * widened, and both deserve a stop.
     */
    @Test
    fun `every address this module names is on this machine`() {
        val elsewhere = addresses()
            .filterValues { hosts -> hosts.any { it !in LOOPBACK } }
            .mapValues { (_, hosts) -> hosts.filterNot { it in LOOPBACK } }

        assertEquals(
            emptyMap(),
            elsewhere,
            "This module names an address that is not this machine. Its client exists to reach " +
                "the server the app itself is holding open; a figure that can be made to wait on " +
                "somebody else's host belongs to the exchange-rate source, and to nothing here.\n" +
                elsewhere.entries.joinToString("\n") { "  ${it.key} — ${it.value}" },
        )
    }

    /**
     * And the constant the addresses are built from is the loopback address itself.
     *
     * Without this the check above would be satisfied by a name, and the name would be free to be
     * anything at all — the indirection would be exactly where the host slipped in.
     */
    @Test
    fun `the constant the addresses are built from is the loopback address`() {
        val declared = sources
            .flatMap { file ->
                Regex("""const\s+val\s+LOOPBACK_HOST\s*=\s*"([^"]*)"""")
                    .findAll(file.readText().withoutComments())
                    .map { file.relativeTo(repoRoot).invariantSeparatorsPath to it.groupValues[1] }
            }

        assertTrue(
            declared.isNotEmpty(),
            "no `LOOPBACK_HOST` is declared in this module any more, so the addresses above are " +
                "built from something this test does not check.",
        )
        assertEquals(
            emptyList(),
            declared.filterNot { (_, address) -> address == "127.0.0.1" },
            "`LOOPBACK_HOST` is not the loopback address: $declared",
        )
    }

    // ------------------------------------------------------------------------------
    // Reading an address out of Kotlin
    // ------------------------------------------------------------------------------

    /** Every host this module spells, by the source that spells it. */
    private fun addresses(): Map<String, List<String>> = sources
        .associate { file ->
            file.relativeTo(repoRoot).invariantSeparatorsPath to hostsIn(file.readText())
        }
        .filterValues { it.isNotEmpty() }

    /**
     * The authority of every URL written in [text] — what is between the scheme and the path.
     *
     * An interpolation is kept as it is written, so `$LOOPBACK_HOST` reads as the name it is and is
     * answered for by the assertion on that constant. Comments are dropped first: the prose here
     * explains what an address is for, and reading it as code would find hosts in the explanation.
     */
    private fun hostsIn(text: String): List<String> =
        Regex("""\b(?:https?|wss?|ftp)://(\[[^]\s"]*]|[^"/\s:]*)""")
            .findAll(text.withoutComments())
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    private fun File.invariantPath(): String = relativeTo(repoRoot).invariantSeparatorsPath

    /**
     * Comments replaced by the newlines they held, and every literal kept whole.
     *
     * Both halves are needed here. Reading a comment as code would find hosts in the prose that
     * explains why there are none — and a scan that skipped to the end of the line on every `//`
     * would delete the one thing it is looking for, because the `//` of a URL is not a comment and
     * is not distinguishable from one without knowing a literal is open.
     */
    private fun String.withoutComments(): String {
        val code = StringBuilder(length)
        var i = 0
        while (i < length) {
            when {
                startsWith(RAW_QUOTE, i) -> {
                    val end = indexOf(RAW_QUOTE, i + RAW_QUOTE.length)
                        .let { if (it < 0) length else it + RAW_QUOTE.length }
                    code.append(substring(i, end))
                    i = end
                }

                this[i] == '"' || this[i] == '\'' -> {
                    val end = closingOf(this[i], from = i + 1)
                    code.append(substring(i, end))
                    i = end
                }

                startsWith("/*", i) -> {
                    val end = indexOf("*/", i).let { if (it < 0) length else it + 2 }
                    substring(i, end).forEach { if (it == '\n') code.append('\n') }
                    i = end
                }

                startsWith("//", i) -> i = indexOf('\n', i).let { if (it < 0) length else it }
                else -> code.append(this[i++])
            }
        }
        return code.toString()
    }

    /** Just past the [quote] that closes the literal opened before [from], escapes honoured. */
    private fun String.closingOf(quote: Char, from: Int): Int {
        var i = from
        while (i < length && this[i] != quote) {
            if (this[i] == '\\') i++
            i++
        }
        return minOf(i + 1, length)
    }

    private companion object {

        /** Where the one client of this module is built. */
        const val BRIDGE = "feature/mcp/impl/src/jvmMain/kotlin/com/neoutils/finsight/mcp/McpBridge.kt"

        /** What opens and closes a raw string, which is where this file keeps its own samples. */
        const val RAW_QUOTE = "\"\"\""

        /**
         * This machine, in every spelling the module uses: the address itself, the name the browser
         * sends for it, its IPv6 form — and the constant the two ends are built from, which the
         * test below pins to the address.
         */
        val LOOPBACK = setOf(
            "127.0.0.1",
            "localhost",
            "[::1]",
            "\$LOOPBACK_HOST",
            "\${LOOPBACK_HOST}",
        )
    }
}
