package com.neoutils.finsight.resources

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app ships in two languages, and **a key that exists in one of them is a defect**.
 *
 * `Res.string.x` resolves against the default file, so a key added only there compiles, runs, and
 * shows Portuguese to an English reader — with nothing failing anywhere. The compiler cannot catch
 * it because the other file is data, not code; this is the only place it can be caught at all.
 */
class StringResourceParityTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val resources = File(repoRoot, "core/resources/src/commonMain/composeResources")

    private val default = File(resources, "values/strings.xml")

    private val english = File(resources, "values-en/strings.xml")

    private fun File.keys(): List<String> = KEY.findAll(readText()).map { it.groupValues[1] }.toList()

    @Test
    fun `every key exists in both languages`() {
        val inDefault = default.keys().toSet()
        val inEnglish = english.keys().toSet()

        assertEquals(
            emptySet(),
            inDefault - inEnglish,
            "translated into Portuguese only: an English reader sees Portuguese, and nothing fails",
        )
        assertEquals(
            emptySet(),
            inEnglish - inDefault,
            "present in English only: `Res.string` resolves against the default file, which has no " +
                "such key",
        )
    }

    @Test
    fun `no key is declared twice in the same file`() {
        listOf(default, english).forEach { file ->
            val duplicates = file.keys().groupingBy { it }.eachCount().filterValues { it > 1 }

            assertTrue(
                duplicates.isEmpty(),
                "${file.name} declares a key more than once, and which one wins is the parser's " +
                    "choice rather than anyone's: ${duplicates.keys}",
            )
        }
    }

    private companion object {
        val KEY = Regex("""<string name="([^"]+)"""")
    }
}
