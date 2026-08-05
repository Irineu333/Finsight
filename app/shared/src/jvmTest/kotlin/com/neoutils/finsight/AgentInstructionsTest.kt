package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **`AGENTS.md` names skills that exist, and every skill that exists.**
 *
 * The file is not documentation: it is an instruction an agent is told to obey, and it
 * obeys by opening the path. A path that no longer resolves therefore fails in the worst
 * possible way — silently, as an instruction to read something that is not there, with
 * nothing anywhere saying the list is stale.
 *
 * It has already drifted twice: `sdd-specify`, `sdd-plan` and `sdd-execute` outlived their
 * files, and four more were removed while the list went on naming them. Twice is a
 * pattern, and a pattern that nothing checks is a third occurrence waiting. Nothing in the
 * compiler can say this, so it is said here.
 *
 * The equality is deliberate in **both** directions. A listed skill that does not exist
 * sends an agent to a missing file; an existing skill that is not listed is one nobody is
 * told to use, which is the quieter half of the same defect — the skill is simply never
 * reached, and no run ever reports it.
 */
class AgentInstructionsTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val instructions = File(repoRoot, "AGENTS.md")

    private val skillsDir = File(repoRoot, ".claude/skills")

    /** Every `./.claude/skills/<name>/SKILL.md` the instructions point at. */
    private val listed: Set<String>
        get() = Regex("""\./\.claude/skills/([^/`]+)/SKILL\.md""")
            .findAll(instructions.readText())
            .map { it.groupValues[1] }
            .toSet()

    /** Every skill on disk — a directory is a skill when it holds a `SKILL.md`. */
    private val present: Set<String>
        get() = skillsDir.listFiles()
            .orEmpty()
            .filter { File(it, "SKILL.md").isFile }
            .map { it.name }
            .toSet()

    @Test
    fun `every skill the instructions name is a skill that exists`() {
        assertEquals(
            present,
            listed,
            "AGENTS.md and .claude/skills disagree about which skills this project has.\n" +
                (listed - present).joinToString("\n") { "  NAMED BUT MISSING: $it — the list is out of date" } +
                (present - listed).joinToString("\n") { "  PRESENT BUT UNNAMED: $it — nobody is told to use it" },
        )
    }

    /**
     * The other half of an instruction that resolves: `CLAUDE.md` is named as the single
     * source of local context, and it is the first thing every task is told to read.
     */
    @Test
    fun `the instructions point at a CLAUDE file that exists`() {
        assertEquals(true, File(repoRoot, "CLAUDE.md").isFile)
        assertEquals(true, "CLAUDE.md" in instructions.readText())
    }
}
