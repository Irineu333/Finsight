package com.neoutils.finsight.backup

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The security-scoped url never crosses a `String`** (task 11.5).
 *
 * A url the iOS document picker grants carries its permission out of band: the sandbox
 * extension token is not part of the address, so `absoluteString` does not serialise it and
 * a url rebuilt from that text is a url that looks identical and opens nothing. The same
 * goes the other way — asking a scoped url for its `path` and handing that path to
 * `NSFileManager` is the round trip in the other direction. Both fail late, in a folder full
 * of somebody's backups, and both look like a bug in something else (design D2).
 *
 * Nothing in the compiler says so. `NSURL.path` is a perfectly ordinary property, and every
 * path-taking method on `NSFileManager` sits one autocomplete away from the url-taking one
 * with the same name. So it is said here, by scanning what was actually written: the folder
 * rung addresses the file system by url and only by url, and any new file that joins it is
 * held to that from the moment it exists.
 *
 * **The exemption is the app's own sandbox, and it is named file by file.** The first rung
 * and both dialogs address places this app owns — Application Support, the temporary area,
 * a copy the picker already put inside the sandbox — where a path is the whole of the
 * address and no scope is being carried anywhere. Adding a file to that list is a decision
 * somebody has to make on purpose.
 */
class ScopedUrlNeverTextTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val iosSources: List<File> =
        File(repoRoot, "feature/backup/impl/src/iosMain")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.name }
            .toList()

    /**
     * The files whose addresses are the app's own, where a path carries no scope and is the
     * only thing Foundation's older API takes.
     */
    private val addressedByPath = setOf(
        // Application Support, which the first rung writes into and which the copy taken
        // before a migration lands in.
        "IosBackupDestination.kt",
        // The temporary area, and a file the picker already copied into the sandbox.
        "IosBackupFileService.kt",
        "IosMigrationCopyPlace.kt",
    )

    /** The files this rule exists for, which must be found or the scan is covering nothing. */
    private val addressedByUrl = setOf(
        "IosBackupFolder.kt",
        "IosFolderBackupDestination.kt",
    )

    /**
     * Turning a url into text, and building one back out of text. Either direction loses the
     * grant, and `absoluteString` is the one Apple documents as destroying it.
     */
    private val textualUrl = listOf(
        Regex("""\babsoluteString\b"""),
        Regex("""\brelativePath\b"""),
        Regex("""\.path\b"""),
        Regex("""\bURLWithString\b"""),
    )

    /**
     * Foundation's path-taking file API, every method of it the folder rung could reach for.
     * Each has a url-taking twin, and the twin is the one that works on a scope.
     */
    private val pathTakingApi = listOf(
        "fileExistsAtPath",
        "contentsOfDirectoryAtPath",
        "attributesOfItemAtPath",
        "copyItemAtPath",
        "moveItemAtPath",
        "removeItemAtPath",
        "createDirectoryAtPath",
        "createFileAtPath",
    ).map { Regex("""\b$it\b""") }

    @Test
    fun `the folder rung addresses the file system by url alone`() {
        val offences = iosSources
            .filter { it.name !in addressedByPath }
            .flatMap { file ->
                val code = file.readText().codeOnly()
                (textualUrl + pathTakingApi)
                    .filter { it.containsMatchIn(code) }
                    .map { "${file.name}: ${it.pattern}" }
            }

        assertEquals(emptyList(), offences)
    }

    @Test
    fun `the scan covers the two files it exists for`() {
        val scanned = iosSources.map { it.name }.toSet()

        assertTrue(
            addressedByUrl.all { it in scanned },
            "the folder rung was renamed or moved: expected $addressedByUrl in $scanned",
        )
    }

    /**
     * An exemption that names a file which is no longer there is an exemption nobody
     * re-examined, and the next file to take that name inherits it silently.
     */
    @Test
    fun `every exempted file still exists`() {
        val scanned = iosSources.map { it.name }.toSet()

        assertEquals(emptySet(), addressedByPath - scanned)
    }
}

/**
 * The file with its comments and its string literals taken out.
 *
 * The rule is about what the code calls, and a rule of this shape has to leave the prose
 * alone or it cannot be explained: the comment above every one of these files names
 * `absoluteString` precisely in order to say why it is never called. A stripper is the
 * honest way to keep both — the alternative is a rule that may not be written down.
 */
private fun String.codeOnly(): String {
    val code = StringBuilder()
    var index = 0

    while (index < length) {
        when {
            startsWith("//", index) ->
                index = indexOf('\n', index).let { if (it < 0) length else it }

            startsWith("/*", index) ->
                index = indexOf("*/", index + 2).let { if (it < 0) length else it + 2 }

            startsWith("\"\"\"", index) ->
                index = indexOf("\"\"\"", index + 3).let { if (it < 0) length else it + 3 }

            this[index] == '"' || this[index] == '\'' -> index = endOfLiteral(index)

            else -> code.append(this[index++])
        }
    }

    return code.toString()
}

/** One past the closing quote of the literal opening at [start], escapes honoured. */
private fun String.endOfLiteral(start: Int): Int {
    val quote = this[start]
    var index = start + 1

    while (index < length && this[index] != quote) {
        index += if (this[index] == '\\') 2 else 1
    }

    return index + 1
}
