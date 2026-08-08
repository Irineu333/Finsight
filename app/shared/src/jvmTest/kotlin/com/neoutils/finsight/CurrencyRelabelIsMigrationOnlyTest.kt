package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Relabelling does not contradict "the currency of an account never changes".**
 *
 * A migration may do what the runtime forbids, because it happens *before* the
 * denomination is an observable fact — the same precedent the project already records
 * for archiving, which generates no write-off at runtime "but the migration does, and
 * migrated data obeys the same rules as new data". After it, immutability holds with
 * no exception.
 *
 * "With no exception" is the part worth checking mechanically, and this is the check:
 * exactly one production file re-denominates a row that already exists, and it is the
 * migration. Anything else is the runtime doing what design D12 forbids.
 */
class CurrencyRelabelIsMigrationOnlyTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val productionSources: List<File> = repoRoot.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" }
        .filter { it.isFile && it.extension == "kt" }
        .filter { file ->
            val path = file.relativeTo(repoRoot).invariantSeparatorsPath
            "/src/" in path && Regex("/src/[a-zA-Z]*Main/") in path
        }
        .toList()

    @Test
    fun `only the migration re-denominates a row that already exists`() {
        // Any statement that assigns to a `currency` column of an existing row. It
        // catches both quoting styles Room queries use in this repository.
        val updatesACurrency = Regex("""UPDATE\s+`?\w+`?\s+SET\s+`?currency`?\s*=""", RegexOption.IGNORE_CASE)

        val found = productionSources
            .filter { updatesACurrency.containsMatchIn(it.readText()) }
            .map { it.relativeTo(repoRoot).invariantSeparatorsPath }
            .toSet()

        assertEquals(
            setOf("core/database/src/commonMain/kotlin/com/neoutils/finsight/database/migration/Migration11To12.kt"),
            found,
            "A production site re-denominates an existing row. The migration is allowed " +
                "to, once, before the denomination is observable; the runtime never is.\n" +
                found.joinToString("\n") { "  FOUND: $it" },
        )
    }

    /**
     * And the app offers no way back. The false positive of design D30 — a user of the
     * legacy currency whose device sits in a foreign region — is relabelled without
     * being asked and cannot undo it, which is the accepted cost rather than an
     * oversight. A "revert relabelling" path appearing anywhere would mean the cost was
     * quietly renegotiated into a feature that reinterprets stored entries.
     */
    @Test
    fun `no production site offers to undo the relabelling`() {
        val undo = Regex("""(undo|revert|rollback)\w*[Rr]elabel""")

        val found = productionSources
            .filter { undo.containsMatchIn(it.readText()) }
            .map { it.relativeTo(repoRoot).invariantSeparatorsPath }
            .toSet()

        assertEquals(emptySet(), found, found.joinToString("\n") { "  FOUND: $it" })
    }
}
