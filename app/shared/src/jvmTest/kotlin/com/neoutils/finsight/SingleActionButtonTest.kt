package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The app has one action button, and it belongs to the shell.**
 *
 * It had ten. One lived in the chrome and nine in the `Scaffold` of a screen, and because a screen
 * that draws its own could not know about the others, a wide window showed two identical buttons at
 * once. Consolidating them was the easy half; keeping them consolidated is this test.
 *
 * Nothing in the compiler can say it. A screen that declares `floatingActionButton` again compiles
 * perfectly, offers a second button beside the shell's, and no run reports it — the regression is
 * silent by construction, which is exactly the kind this project writes a structural test for.
 *
 * A screen offers actions by publishing them (`ChromeEffect`), and the shell decides the form. The
 * shell is therefore the one place the slot may be named, and it does not name it either: its
 * button is drawn outside the slot, so that the scrim of an open menu can cover the bottom bar.
 */
class SingleActionButtonTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val slot = Regex("""\bfloatingActionButton\s*=""")

    /** Every Kotlin source of every feature `impl`, the shell included. */
    private val featureSources: List<File>
        get() = File(repoRoot, "feature")
            .listFiles()
            .orEmpty()
            .map { File(it, "impl/src") }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { file -> file.isFile && file.extension == "kt" } }

    @Test
    fun `no screen declares a floating action button of its own`() {
        val offenders = featureSources
            .filter { slot.containsMatchIn(it.readText()) }
            .map { it.relativeTo(repoRoot).path }
            .sorted()

        assertEquals(
            emptyList(),
            offenders,
            "A `Scaffold` declares its own action button again. The app has exactly one, drawn by " +
                "the shell; a screen offers what it does by publishing actions with `ChromeEffect`.\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }
}
