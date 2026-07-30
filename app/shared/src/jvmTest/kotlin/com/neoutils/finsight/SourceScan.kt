package com.neoutils.finsight

import java.io.File

/**
 * Reading the app's own sources, for the rules no compiler reaches.
 *
 * A few facts in this codebase are about the whole app rather than about any one module — where
 * conversion may happen, where a term of a figure may be left out — so the only place they can
 * be stated is here, over the files. Shared between the suites that state them, because a second
 * copy of the walk would be a second definition of what counts as production code.
 */
internal class Source(val path: String, val text: String) {
    /**
     * The file with its comments removed. Without this, prose *about* a rule reads as breaking
     * it: a KDoc line explaining why something is forbidden contains the very thing the pattern
     * looks for.
     */
    val code: String get() = text
        .replace(BLOCK_COMMENT, "")
        .replace(LINE_COMMENT, "")

    private companion object {
        val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("//[^\n]*")
    }
}

internal fun sourcesUnder(vararg roots: String): List<Source> {
    val repository = repositoryRoot()
    return roots
        .map { repository.resolve(it) }
        .flatMap { it.walkTopDown().toList() }
        .filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(repository).path }
        .filterNot { it.contains("/build/") }
        // Test sources are where a boundary is exercised from both sides.
        .filterNot { it.contains("Test/kotlin") }
        .map { Source(it, repository.resolve(it).readText()) }
}

internal fun repositoryRoot(): File {
    var candidate: File? = File("").absoluteFile
    while (candidate != null && !candidate.resolve("settings.gradle.kts").isFile) {
        candidate = candidate.parentFile
    }
    return requireNotNull(candidate) { "Could not locate the repository root." }
}
