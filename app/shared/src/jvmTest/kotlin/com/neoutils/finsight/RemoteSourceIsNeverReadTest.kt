package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The remote source is a writer of the archive, and never a path of reading it.**
 *
 * This is the guarantee the whole change rests on. A synchronisation that *writes* rows
 * leaves every figure reading the same local table it always read — offline, with no
 * loading state and no possible failure. A source consulted *when a rate is missing* would
 * put the network in the path of a balance, and a balance that fails because a host is
 * unreachable is worse than one that stacks terms: the second is honest, the first is an
 * error screen about money that exists.
 *
 * The difference between the two is not visible at runtime and not visible to the
 * compiler. It is visible in **who is allowed to name the port**, so that is what is
 * pinned here, by name and by hand. A fifth file naming `IRemoteRateSource` is either a
 * read path being opened or this list being out of date, and both deserve a stop.
 */
class RemoteSourceIsNeverReadTest {

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

    private val allowed = setOf(
        // The declaration itself, which holds no HTTP client and names no provider.
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/repository/IRemoteRateSource.kt",
        // The one implementation, over Ktor, in the one module allowed to speak HTTP.
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/network/FrankfurterRateSource.kt",
        // The one consumer: the upkeep, which writes what it learns as ordinary rows.
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/SyncExchangeRatesUseCase.kt",
        // And the binding that joins the two.
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/di/SettingsModule.kt",
    )

    /**
     * The state of the upkeep has **exactly one surface**, and it is the rates screen.
     *
     * That screen is not a figure — it is the archive explaining itself, and it is where
     * the *out of date for more than 30 days* signal already lives, which without the
     * synchronisation context is an accusation with no defendant. Any *other* screen
     * showing it would be a consolidated figure explaining why it is late, which is the
     * loading state the whole change exists to keep off a balance. Nothing in the compiler
     * says so, so it is said here.
     */
    private val syncStateAllowed = setOf(
        // The declaration, and the pair it is keyed by.
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/repository/IRateSyncStateRepository.kt",
        // The one writer: the upkeep, which persists what it managed to answer.
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/SyncExchangeRatesUseCase.kt",
        // Deleting a currency, which **forgets** rather than shows: what the upkeep holds
        // about a currency stops being true when the currency stops existing, and codes
        // are reusable, so a stamp left behind would block the next currency to wear that
        // code on its first day. It reads the state only to write the state without it —
        // no surface of the app learns anything about the synchronisation from here.
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/DeleteCurrencyUseCase.kt",
        // The one implementation, over the settings the app already keeps.
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/RateSyncStateRepository.kt",
        // The one reader: the rates screen's view model.
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/exchangeRates/ExchangeRatesViewModel.kt",
        // And the bindings that join them.
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/di/SettingsModule.kt",
    )

    @Test
    fun `only the rates screen reads the state of the upkeep`() {
        val found = productionSources
            .filter { "IRateSyncStateRepository" in it.readText() }
            .map { it.relativeTo(repoRoot).invariantSeparatorsPath }
            .toSet()

        assertEquals(
            syncStateAllowed,
            found,
            "A production site outside the rates screen reads the state of the " +
                "synchronisation. It has one surface on purpose: anywhere else it is a " +
                "consolidated figure explaining why it is late, which is the loading " +
                "state a balance may never carry.\n" +
                (found - syncStateAllowed).joinToString("\n") { "  NEW: $it" } +
                (syncStateAllowed - found).joinToString("\n") { "  GONE: $it — the list is out of date" },
        )
    }

    @Test
    fun `only the writer path names the remote source`() {
        val found = productionSources
            .filter { "IRemoteRateSource" in it.readText() }
            .map { it.relativeTo(repoRoot).invariantSeparatorsPath }
            .toSet()

        assertEquals(
            allowed,
            found,
            "A production site outside the writer path names the remote rate source. " +
                "No read, view model, reducer or screen may reach it: the archive is " +
                "written by the network and read offline, and reversing that direction " +
                "is what puts a spinner on a balance.\n" +
                (found - allowed).joinToString("\n") { "  NEW: $it" } +
                (allowed - found).joinToString("\n") { "  GONE: $it — the list is out of date" },
        )
    }

    /**
     * The reciprocal half, and the structural one: a `:core:network` was rejected precisely
     * so that "only one module may reach the network" would be a fact about the module
     * graph rather than a matter of discipline (design D11). This is that fact, checked.
     */
    @Test
    fun `no module outside the settings feature declares Ktor`() {
        val ktor = Regex("""ktor""", RegexOption.IGNORE_CASE)
        val owner = "feature/settings/impl/build.gradle.kts"

        val found = repoRoot.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" }
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .filter { ktor.containsMatchIn(it.readText()) }
            .map { it.relativeTo(repoRoot).invariantSeparatorsPath }
            .toSet()

        assertEquals(
            setOf(owner),
            found,
            "Ktor left the one module that may hold it. The restriction is the module " +
                "graph, not discipline: a second module with a client is an invitation " +
                "for a figure to wait on one.\n" +
                (found - setOf(owner)).joinToString("\n") { "  NEW: $it" },
        )
    }
}
