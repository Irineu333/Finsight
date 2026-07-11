package com.neoutils.finsight

import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.feature.dashboard.api.DashboardEntry
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * `homeGraph()` resolve os entries fora de escopo Composable, via `KoinPlatform.getKoin()`.
 * Um binding ausente só falharia na primeira composição do NavHost — este teste antecipa isso.
 */
class AppModulesTest {

    @Test
    fun appModulesResolveTheEntriesRequiredToBuildTheHomeGraph() {
        val koin = koinApplication { modules(appModules) }.koin

        assertNotNull(koin.get<DashboardEntry>())
        assertNotNull(koin.get<TransactionsEntry>())
    }
}
