package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The app ships in two languages, and every key exists in both.**
 *
 * A key present in only one file is not a missing translation the user notices: Compose Resources
 * falls back to the default file, so the app goes on running and quietly speaks Portuguese to an
 * English reader — or fails to resolve at all, depending on which side is missing. Either way
 * nothing says so, and the divergence survives every build.
 *
 * The check is an equality of key sets, in both directions. A key added to `values/` and forgotten
 * in `values-en/` is the common half; the reverse is the same defect seen from the other side.
 */
class StringTranslationParityTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val resources = File(repoRoot, "core/resources/src/commonMain/composeResources")

    private val key = Regex("""<string\s+name="([^"]+)"""")

    private fun keysOf(language: String): Set<String> =
        key.findAll(File(resources, "$language/strings.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `every string key is declared in both languages`() {
        val pt = keysOf("values")
        val en = keysOf("values-en")

        assertEquals(
            pt,
            en,
            "The two string files disagree about which keys this app has.\n" +
                (pt - en).joinToString("\n") { "  MISSING IN ENGLISH: $it" } +
                (en - pt).joinToString("\n") { "  MISSING IN PORTUGUESE: $it" },
        )
    }
}
