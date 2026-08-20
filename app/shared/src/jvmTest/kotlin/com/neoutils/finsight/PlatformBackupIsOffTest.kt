package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The app does not delegate backup to the platform.**
 *
 * Stated as an inspection because it is a property of *what is declared*, not of what
 * runs: the two mechanisms live in a manifest and two resource files, and nothing that
 * executes in a unit test can observe them. The same treatment `RateIsNeverWrittenTest`
 * gives the schema version, for the same reason.
 *
 * It is worth a test at all because the failure is silent in both directions. Leaving
 * `allowBackup` on means the database keeps going to Google's cloud as three files copied
 * without transactional coordination — a `.db`, a `-wal` and a `-shm` that may not add up
 * to a database when restored. And since Android 12 the attribute alone governs only the
 * cloud: device-to-device transfer is ruled by a section of `dataExtractionRules` whose
 * *omission* enables the mode outright. A file that forgets `<device-transfer>` reads
 * exactly like a file that never meant to allow it.
 */
class PlatformBackupIsOffTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val manifest = File(repoRoot, "app/android/src/main/AndroidManifest.xml").readText()

    private fun resource(name: String) = File(repoRoot, "app/android/src/main/res/xml/$name")

    @Test
    fun `the manifest turns the platform's own backup off`() {
        assertTrue(
            """android:allowBackup\s*=\s*"false"""".toRegex().containsMatchIn(manifest),
            "allowBackup is what sends the database to the cloud without anyone deciding it",
        )
    }

    @Test
    fun `the manifest names both rule files, because minSdk needs both formats`() {
        assertTrue(
            """android:dataExtractionRules\s*=\s*"@xml/data_extraction_rules"""".toRegex()
                .containsMatchIn(manifest),
            "the API 31+ format",
        )
        assertTrue(
            """android:fullBackupContent\s*=\s*"@xml/backup_rules"""".toRegex()
                .containsMatchIn(manifest),
            "the API 24-30 format, which minSdk still reaches",
        )
    }

    @Test
    fun `the extraction rules refuse both the cloud and the device transfer`() {
        val rules = resource("data_extraction_rules.xml")
        assertTrue(rules.exists(), "the file the manifest names has to be there")

        val text = rules.readText()
        listOf("cloud-backup", "device-transfer").forEach { section ->
            assertTrue(
                text.contains("<$section"),
                "omitting <$section> does not disable the mode — it enables it outright",
            )
        }
        assertTrue(text.contains("<exclude"), "the sections have to exclude something")
        assertFalse(
            text.contains("<include"),
            "an include narrows the exclusion to everything else, which is the opposite",
        )
        listOf("database", "root").forEach { domain ->
            assertTrue(
                text.contains("""domain="$domain""""),
                "the archive lives under $domain and has to be named",
            )
        }
    }

    @Test
    fun `the legacy rules exist for the versions that still read them`() {
        val rules = resource("backup_rules.xml")
        assertTrue(rules.exists(), "API 24 to 30 never look at the other file")

        val text = rules.readText()
        assertTrue(text.contains("<full-backup-content"), "the older format has a root of its own")
        assertTrue(text.contains("<exclude"))
        assertFalse(text.contains("<include"))
    }

    @Test
    fun `iOS keeps all three database files out of iCloud`() {
        val source = File(
            repoRoot,
            "core/database/src/iosMain/kotlin/com/neoutils/finsight/database/Database.ios.kt",
        ).readText()

        assertTrue(
            source.contains("NSURLIsExcludedFromBackupKey"),
            "a document directory is in iCloud's backup by default; only this takes it out",
        )
        listOf("-wal", "-shm").forEach { companion ->
            assertTrue(
                source.contains(companion),
                "excluding the $companion is not optional: the three travel together or the " +
                    "one that arrives is not a database",
            )
        }
    }
}
