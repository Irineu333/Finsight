package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Whoever assembles the database asks where the copy taken before a migration goes — on
 * all three platforms.**
 *
 * Stated as an inspection because the alternative cannot be run. The question is asked in
 * `databasePlatformModule`, which has an `actual` per platform; the desktop one is the only
 * one a unit test reaches, and exercising even that would mean building over the developer's
 * own `~/.finance/finsight.db`, reading it and copying it somewhere. So what is checked is
 * what is *declared*, the same treatment `PlatformBackupIsOffTest` gives a manifest.
 *
 * It is worth checking at all because losing it is silent in every other way. `getOrNull` on
 * an unclaimed port is a valid graph and a deliberate one — a build with nobody to ask takes
 * no copy — so deleting the line compiles, resolves, passes every test, and quietly stops
 * protecting the one update where a migration finishes without an error and writes something
 * wrong. On somebody's device, discovered days later, with nothing to restore.
 *
 * The Android and iOS lines are the ones this is really for: nothing else in the suite opens
 * either of those files.
 */
class PreMigrationCopyIsAskedForTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun platformModule(platform: String) = File(
        repoRoot,
        "core/database/src/${platform}Main/kotlin/com/neoutils/finsight/di/" +
            "DatabaseModule.$platform.kt",
    )

    @Test
    fun `every platform hands the builder what the port answers`() {
        listOf("android", "ios", "jvm").forEach { platform ->
            val module = platformModule(platform)
            assertTrue(module.exists(), "${module.name} is where the database is assembled")

            val text = module.readText()
            assertTrue(
                """getOrNull<PreMigrationCopyTarget>\(\)\s*\?\.path\(\)""".toRegex()
                    .containsMatchIn(text),
                "$platform assembles the database without asking where the copy before a " +
                    "migration goes, so on that platform an update is no longer preceded " +
                    "by one — and nothing else would say so",
            )
            assertTrue(
                """captureInto\s*=""".toRegex().containsMatchIn(text),
                "$platform asks, and then does not pass the answer on",
            )
        }
    }
}
