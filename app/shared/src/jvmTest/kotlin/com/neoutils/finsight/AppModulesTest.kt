package com.neoutils.finsight

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.repository.ExchangeRateRepository
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.PreMigrationCopyTarget
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.domain.ledger.TransactionRemovalPrelude
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.SyncExchangeRatesUseCase
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.McpEntry
import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.domain.vault.service.BackupDestination
import com.neoutils.finsight.domain.vault.service.BackupFileService
import com.neoutils.finsight.domain.vault.service.OwnCopyCheck
import com.neoutils.finsight.feature.backup.api.PeriodicBackup
import com.neoutils.finsight.feature.backup.api.PreventiveBackup
import com.neoutils.finsight.feature.settings.api.SettingsGraph
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.support.api.SupportGraph
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * `TransactionsEntry` e `NavCatalog` são resolvidos via Koin fora de escopo Composable por várias
 * features (dashboard, accounts, creditcards, report) para abrir modais de transação e alimentar
 * a chrome/grid de navegação. Um binding ausente só falharia na primeira composição — este teste
 * antecipa isso.
 */
class AppModulesTest {

    @Test
    fun appModulesResolveTheCrossFeatureTransactionsEntry() {
        val koin = koinApplication { modules(appModules) }.koin

        assertNotNull(koin.get<TransactionsEntry>())
    }

    /**
     * The ledger takes the `RoomDatabase` supertype, not `AppDatabase` — so the graph
     * only closes if the database is bound under both. It was not, and nothing
     * noticed: every jvm test builds its repositories by hand, so the suite stayed
     * green while the app crashed on the first screen that touched a transaction.
     */
    @Test
    fun appModulesResolveTheLedgerRepositories() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<ITransactionRepository>())
        assertNotNull(koin.get<IEntryRepository>())
    }

    /**
     * And the *same* instance under both types: the removal hook runs inside the
     * ledger's write transaction and opens the facade's, which nests only while they
     * share one connection pool. Two instances would deadlock, not fail.
     */
    @Test
    fun theLedgerAndTheFacadesShareOneDatabase() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertSame(koin.get<AppDatabase>(), koin.get<RoomDatabase>())
    }

    /**
     * The consolidation layer declares both repositories in `:core:model` and the
     * settings feature implements them. Nothing in `:core:model` can name the
     * implementation, so the only thing that closes the graph is the shell binding
     * `settingsModule` — and a miss would surface as the first consolidated figure
     * crashing, not as a compile error.
     */
    @Test
    fun appModulesResolveTheConsolidationRepositories() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<IBaseCurrencyRepository>())
        assertNotNull(koin.get<IExchangeRateRepository>())
    }

    /**
     * The upkeep of the archive is resolved by `App` in a `LaunchedEffect`, which no
     * compiler checks: a missing binding here would not fail the build, it would crash
     * the user's app on launch. And it closes only if all three of its own ports do —
     * the remote source, the sync state and the concrete archive the rates screens read.
     */
    @Test
    fun appModulesResolveTheRateSynchronisation() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<IRemoteRateSource>())
        assertNotNull(koin.get<IRateSyncStateRepository>())
        assertNotNull(koin.get<SyncExchangeRatesUseCase>())
    }

    /** One archive, not two: the interface resolves from the concrete binding. */
    @Test
    fun theArchiveIsOneInstanceUnderBothTypes() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertSame(koin.get<ExchangeRateRepository>(), koin.get<IExchangeRateRepository>())
    }

    /**
     * The MCP controller is bound by an `expect`/`actual` platform module that only the shell
     * aggregates. Leaving `mcpModule` off `appModules` compiles on every target and fails
     * where nothing is watching: the desktop process resolves the controller outside any
     * composition, while starting up.
     */
    @Test
    fun appModulesResolveTheMcpServerController() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<McpServerController>())
    }

    /**
     * The activity log is bound in the same feature module as the controller but closes over the
     * database instead of a socket, so it can be missing while the controller resolves fine. The
     * section that reads it resolves it outside composition, like everything else here.
     */
    @Test
    fun appModulesResolveTheAgentActivityLog() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<IAgentActivityRepository>())
    }

    /**
     * The MCP section's graph is built inside the settings graph, which resolves the entry point
     * that registers it while the `NavHost` is being assembled — outside any composition, and before
     * a single screen exists. A missing binding takes the whole navigation graph down on the first
     * frame, not just the section it belongs to.
     */
    @Test
    fun appModulesResolveTheMcpEntryPoint() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<McpEntry>())
    }

    /**
     * The verifier stands between a file the user picked and their entire archive, and it
     * is resolved outside any Composable. A missing binding would not fail the build — it
     * would crash the first restore anyone attempted, which is the single moment in this
     * app where failing is least affordable.
     *
     * It closes only if the platform module also provides a way to build a database over
     * an arbitrary path: on Android that factory has to close over a `Context`, which is
     * why it exists at all instead of the feature assembling the builder itself.
     */
    @Test
    fun appModulesResolveTheCandidateVerifier() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<CandidateVerifier>())
    }

    /**
     * The backup feature is reached from settings, not from the navigation catalog, so
     * nothing in the graph proves it was aggregated. A missing `backupModule` would
     * compile, render the entry, and crash on the first tap — the file picker is what the
     * screen cannot be built without.
     */
    @Test
    fun appModulesResolveTheBackupFeature() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<BackupFileService>())
    }

    /**
     * The vault's two triggers that nothing in the graph reaches for. `App` resolves the
     * periodic one in a `LaunchedEffect`, and `:core:database` asks for the other one with
     * `getOrNull` while it assembles the database — an unclaimed port there is a valid
     * graph, so nothing would fail. A miss in either is an app that compiles, renders, and
     * silently stops taking the copies it says it takes.
     */
    @Test
    fun appModulesResolveTheVaultTriggersNobodyCompilesAgainst() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<PeriodicBackup>())
        assertNotNull(koin.get<PreMigrationCopyTarget>())
    }

    /**
     * The third trigger, and the one other features ask for by interface — a deletion in
     * settings or in a card asks `PreventiveBackup` for a copy without knowing what takes
     * it. Beside it, the two things every trigger ends up needing: the destination the
     * copy lands in, which each platform binds for itself, and the check that decides
     * whether a file in that destination is one this app wrote before retention removes
     * it. None of the three is reached from composition, so nothing here fails to compile.
     */
    @Test
    fun appModulesResolveTheVaultThePreventiveTriggerWritesInto() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<PreventiveBackup>())
        assertNotNull(koin.get<BackupDestination>())
        assertNotNull(koin.get<OwnCopyCheck>())
    }

    /**
     * The one ledger port the graph is *allowed* to leave unclaimed: `ledgerModule`
     * resolves it with `getOrNull()` and falls back to doing nothing, because a removal is
     * complete without it. That fallback is what makes the binding worth a test — a backup
     * module that stopped claiming this port would build, start, delete transactions,
     * installments and invoices with no copy kept back, and leave every ledger test green.
     */
    @Test
    fun appModulesClaimTheLedgersOptionalRemovalPrelude() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<TransactionRemovalPrelude>())
    }

    @Test
    fun appModulesResolveTheNavCatalog() {
        val koin = koinApplication { modules(appModules) }.koin

        val catalog = koin.get<NavCatalog>()

        assertTrue(catalog.destinations.isNotEmpty())
    }

    /**
     * The catalog is the single source of truth projected into the rail (`isOffered`), the mobile
     * bottom bar (`primaryTab`) and the mobile grid (`!primaryTab`). Guard those projections and the
     * persistence-key uniqueness the dashboard grid relies on.
     *
     * `isOffered` is the platform axis and it points both ways: a destination with no desktop
     * implementation and one that exists only on the desktop are both answered by it.
     */
    @Test
    fun navCatalogProjectionsAreConsistent() {
        val koin = koinApplication { modules(appModules) }.koin
        val destinations = koin.get<NavCatalog>().destinations

        // Exactly two primary tabs feed the mobile bottom bar.
        assertEquals(2, destinations.count { it.primaryTab })

        // A primary tab is restricted to no platform (tabs exist on every form factor).
        assertTrue(destinations.none { it.primaryTab && it.onlyOn != null })

        // The rail carries what this platform offers, and nothing it does not.
        val railCount = destinations.count { it.isOffered }
        assertEquals(destinations.size - destinations.count { !it.isOffered }, railCount)

        // Support is now supported on desktop: it is restricted to no platform and is in the rail.
        val support = destinations.single { it.route == SupportGraph }
        assertTrue(!support.mobileOnly)
        assertTrue(destinations.filter { it.isOffered }.contains(support))

        // Settings sits immediately before Support: the two are both "about the app",
        // and Support's KDoc records being last on purpose.
        assertEquals(
            destinations.indexOfFirst { it.route == SupportGraph } - 1,
            destinations.indexOfFirst { it.route == SettingsGraph },
        )

        // Persistence keys (route type names) must be unique — the grid stores hidden actions by them.
        val keys = destinations.map { it.route::class.simpleName }
        assertEquals(keys.size, keys.toSet().size)
    }
}

/** Keeps the graph check off the user's real desktop database file. */
private val inMemoryDatabase = module {
    single<RoomDatabase.Builder<AppDatabase>> {
        Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver())
    }
}
