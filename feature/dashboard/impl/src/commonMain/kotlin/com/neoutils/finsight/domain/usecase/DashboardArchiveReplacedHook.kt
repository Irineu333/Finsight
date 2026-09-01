package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ArchiveReplacedHook
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.ui.screen.dashboard.AccountsOverviewConfig
import com.neoutils.finsight.ui.screen.dashboard.CreditCardsPagerConfig
import com.neoutils.finsight.ui.screen.dashboard.TotalBalanceConfig

/**
 * Forgets every dashboard preference that names an account or a card by its database id,
 * once a restore has replaced the archive those ids were about.
 *
 * **The three keys this owns.** [TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS] and
 * [AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS] are, in this build, the same string — one
 * excludes an account from the total, the other from the accounts list — and
 * [CreditCardsPagerConfig.EXCLUDED_CARD_IDS] does the same for the credit cards pager.
 * Nothing else this feature saves names a row by id: the other keys are counts, booleans,
 * a spacing flag, or hidden quick-action *destinations* — a route, not a row.
 *
 * **Why every preference is walked rather than only the three widgets known to use these
 * keys today.** A key absent from a `config` map is removed for free — `Map.minus` on a
 * key that is not there is the same map — so there is no branch to keep in step with
 * [com.neoutils.finsight.ui.screen.dashboard.DashboardComponentType] as it grows. The cost
 * is the same either way: nothing, on every preference these keys are not in.
 *
 * **Why the whole preference is not simply reset instead.** The rest of a widget's config —
 * its spacing, its header, `hide_single_account` — is a fact about how the user likes the
 * widget shown, and none of it references a row that a restore could have moved. Erasing it
 * over an id it never carried would trade one small, honest surprise (an exclusion silently
 * lifted) for a larger one (a widget silently reset to its defaults).
 *
 * @see ArchiveReplacedHook for why filtering the ids down to the ones that still resolve is
 * not the fix: a fresh archive assigns its own ids from one, so a stale id is exactly as
 * likely to now name an unrelated row as to name none at all.
 */
class DashboardArchiveReplacedHook(
    private val repository: IDashboardPreferencesRepository,
) : ArchiveReplacedHook {

    override suspend fun onArchiveReplaced() {
        val current = repository.observe().value ?: return
        val cleared = current.map { it.copy(config = it.config - STALE_ROW_ID_KEYS) }

        if (cleared != current) repository.save(cleared)
    }

    private companion object {
        val STALE_ROW_ID_KEYS = setOf(
            TotalBalanceConfig.EXCLUDED_ACCOUNT_IDS,
            AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS,
            CreditCardsPagerConfig.EXCLUDED_CARD_IDS,
        )
    }
}
