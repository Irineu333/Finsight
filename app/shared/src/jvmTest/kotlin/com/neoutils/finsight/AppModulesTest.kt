package com.neoutils.finsight

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.domain.usecase.SyncExchangeRatesUseCase
import com.neoutils.finsight.database.repository.ExchangeRateRepository
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.IMcpServerSettingsRepository
import com.neoutils.finsight.feature.mcp.api.IMcpServerStateSource
import com.neoutils.finsight.feature.mcp.api.McpEntry
import com.neoutils.finsight.feature.settings.api.SettingsGraph
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.support.api.SupportGraph
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.mcp.McpServerController
import com.neoutils.finsight.mcp.contract.ToolRegistry
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
     * The MCP server closes across **two** modules the shell aggregates: the feature's, which owns
     * the configuration, the journal and the screen, and `:app:mcp`'s, which owns the tools and
     * the socket. Neither can name the other's implementations, so a miss would surface as the
     * settings graph failing to build its destination — at navigation time, not at compile time.
     */
    @Test
    fun appModulesResolveTheMcpFeatureAndItsServer() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertNotNull(koin.get<IMcpServerSettingsRepository>())
        assertNotNull(koin.get<IAgentActivityRepository>())
        // Resolved from a `NavGraphBuilder` lambda, which is not `@Composable` and therefore has
        // no compiler telling it the binding is there.
        assertNotNull(koin.get<McpEntry>())
        // The whole server: thirteen tools, the resources, the prompts and the controller. Every
        // one of its dependencies comes from another feature's module.
        assertNotNull(koin.get<McpServerController>())
        assertEquals(13, koin.get<ToolRegistry>().tools.size)
    }

    /** The screen reads the server's state through the port, and it is the controller's own. */
    @Test
    fun theServerStateTheScreenReadsIsTheControllersOwn() {
        val koin = koinApplication { modules(appModules + inMemoryDatabase) }.koin

        assertSame(
            koin.get<McpServerController>().state,
            koin.get<IMcpServerStateSource>().state,
        )
    }

    @Test
    fun appModulesResolveTheNavCatalog() {
        val koin = koinApplication { modules(appModules) }.koin

        val catalog = koin.get<NavCatalog>()

        assertTrue(catalog.destinations.isNotEmpty())
    }

    /**
     * The catalog is the single source of truth projected into the desktop rail (`!mobileOnly`), the
     * mobile bottom bar (`primaryTab`) and the mobile grid (`!primaryTab`). Guard those projections
     * and the persistence-key uniqueness the dashboard grid relies on.
     */
    @Test
    fun navCatalogProjectionsAreConsistent() {
        val koin = koinApplication { modules(appModules) }.koin
        val destinations = koin.get<NavCatalog>().destinations

        // Exactly two primary tabs feed the mobile bottom bar.
        assertEquals(2, destinations.count { it.primaryTab })

        // A primary tab is never mobile-only (tabs exist on every form factor).
        assertTrue(destinations.none { it.primaryTab && it.mobileOnly })

        // The desktop rail excludes mobile-only destinations.
        val railCount = destinations.count { !it.mobileOnly }
        assertEquals(destinations.size - destinations.count { it.mobileOnly }, railCount)

        // Support is now supported on desktop: it is not mobile-only and appears in the rail projection.
        val support = destinations.single { it.route == SupportGraph }
        assertTrue(!support.mobileOnly)
        assertTrue(destinations.filter { !it.mobileOnly }.contains(support))

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
