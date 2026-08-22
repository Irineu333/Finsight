package com.neoutils.finsight.feature.accounts.impl

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.feature.accounts.api.AccountsEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.accountForm.AccountFormModal
import com.neoutils.finsight.ui.modal.transferBetweenAccounts.TransferBetweenAccountsModal

internal class AccountsEntryImpl : AccountsEntry {
    override fun accountFormModal(account: Account?): Modal = AccountFormModal(account)
    override fun editTransferModal(transaction: Transaction): Modal =
        TransferBetweenAccountsModal(transaction)
}
