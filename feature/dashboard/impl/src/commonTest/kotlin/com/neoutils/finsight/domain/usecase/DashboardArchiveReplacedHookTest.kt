package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.database.repository.DashboardPreferencesRepository
import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.ui.screen.dashboard.AccountsOverviewConfig
import com.neoutils.finsight.ui.screen.dashboard.CreditCardsPagerConfig
import com.neoutils.finsight.ui.screen.dashboard.DashboardComponentType
import com.neoutils.finsight.ui.screen.dashboard.TotalBalanceConfig
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The report's own defect: `excluded_account_ids` and `excluded_card_ids` name a row of
 * *this* archive by its database id, in a preference a restore never replaces. Restoring a
 * copy from another install leaves the id on file while the archive underneath it becomes a
 * different one — and a fresh archive assigns its own ids from one, so the id is exactly as
 * likely to now name an unrelated row as to name none at all (`ArchiveReplacedHook`).
 * Filtering down to ids that still resolve would leave that collision case untouched, so
 * this forgets the preference outright rather than trying to repair it.
 */
class DashboardArchiveReplacedHookTest {

    private val settings = MapSettings()
    private val repository = DashboardPreferencesRepository(settings)
    private val hook = DashboardArchiveReplacedHook(repository)

    @Test
    fun `an excluded account no longer excludes anything after a restore`() = runTest {
        repository.save(
            listOf(
                DashboardComponentPreference(
                    key = DashboardComponentType.TOTAL_BALANCE.key,
                    position = 0,
                    config = mapOf(TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS to "3,7"),
                ),
            )
        )

        hook.onArchiveReplaced()

        val total = assertNotNull(repository.observe().value)
            .single { it.key == DashboardComponentType.TOTAL_BALANCE.key }
        assertTrue(
            TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS !in total.config,
            "an id from the old archive is still excluding an account in the new one",
        )
    }

    @Test
    fun `every widget that names a row by id is forgotten in one pass, and nothing else is`() =
        runTest {
            repository.save(
                listOf(
                    DashboardComponentPreference(
                        key = DashboardComponentType.ACCOUNTS_OVERVIEW.key,
                        position = 0,
                        config = mapOf(
                            AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS to "1",
                            AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT to "true",
                        ),
                    ),
                    DashboardComponentPreference(
                        key = DashboardComponentType.CREDIT_CARDS_PAGER.key,
                        position = 1,
                        config = mapOf(CreditCardsPagerConfig.EXCLUDED_CARD_IDS to "5"),
                    ),
                )
            )

            hook.onArchiveReplaced()

            val reloaded = assertNotNull(repository.observe().value)
            val staleKeysLeft = reloaded.flatMap { it.config.keys }.toSet() intersect setOf(
                AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS,
                CreditCardsPagerConfig.EXCLUDED_CARD_IDS,
            )
            assertEquals(emptySet(), staleKeysLeft, "an id-indexed key survived the restore")

            val accounts = reloaded.single { it.key == DashboardComponentType.ACCOUNTS_OVERVIEW.key }
            assertEquals(
                "true",
                accounts.config[AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT],
                "a preference naming nothing by id was erased along with the ones that do",
            )
        }

    @Test
    fun `nothing saved yet is nothing to forget`() = runTest {
        hook.onArchiveReplaced()

        assertNull(repository.observe().value, "a restore invented a saved dashboard")
    }
}
