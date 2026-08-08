package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.DisplayAmount

/**
 * A pending recurring as the section shows it: the template itself — the card reads its
 * label, its nature and its category from it, and the confirm modal takes it whole — plus
 * the [amount] already denominated by the account or card the template posts to
 * (design D17). Resolving that currency needs a suspending read, so it is the builder's
 * job and never the composable's.
 */
data class PendingRecurringUi(
    val recurring: Recurring,
    val amount: DisplayAmount,
)
