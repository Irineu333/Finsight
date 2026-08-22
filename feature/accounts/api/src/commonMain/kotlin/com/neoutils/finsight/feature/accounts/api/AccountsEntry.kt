package com.neoutils.finsight.feature.accounts.api

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.component.Modal

interface AccountsEntry {
    fun accountFormModal(account: Account? = null): Modal

    /**
     * The form of a transfer, in correction mode.
     *
     * Only the correction crosses this boundary. Registering a transfer is born on the
     * accounts screen, inside the module that owns the form, and never needed an entry
     * point; a single member covering both modes would have to take everything as
     * nullable and would accept two states that mean nothing.
     */
    fun editTransferModal(transaction: Transaction): Modal
}
