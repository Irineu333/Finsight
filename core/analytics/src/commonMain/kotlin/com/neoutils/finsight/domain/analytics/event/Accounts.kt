package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event

class CreateAccount(params: Map<String, String>) : Event("create_account", params) {
    constructor(isDefault: Boolean) : this(mapOf("is_default" to isDefault.toString()))
}

class EditAccount(params: Map<String, String>) : Event("edit_account", params) {
    constructor(isDefault: Boolean) : this(mapOf("is_default" to isDefault.toString()))
}

/** The row is gone. Retiring one that must be preserved is [ArchiveAccount] instead. */
object DeleteAccount : Event("delete_account")

/** Retired but kept, and reversible by [UnarchiveAccount] — not a deletion. */
object ArchiveAccount : Event("archive_account")

object UnarchiveAccount : Event("unarchive_account")

object AdjustAccountBalance : Event("adjust_account_balance")

object TransferBetweenAccounts : Event("transfer_between_accounts")

/** A transfer corrected in place, as opposed to deleted and registered again. */
object EditTransferBetweenAccounts : Event("edit_transfer_between_accounts")

/** A yield launched on an account — a transaction, not a balance adjustment (D1). */
object LaunchYield : Event("launch_yield")
