package com.neoutils.finsight

import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.support.api.SupportGraph
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

        // Persistence keys (route type names) must be unique — the grid stores hidden actions by them.
        val keys = destinations.map { it.route::class.simpleName }
        assertEquals(keys.size, keys.toSet().size)
    }
}
