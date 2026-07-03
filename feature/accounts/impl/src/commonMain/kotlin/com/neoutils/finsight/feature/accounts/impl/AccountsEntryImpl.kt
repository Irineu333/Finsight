package com.neoutils.finsight.feature.accounts.impl

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.feature.accounts.api.AccountsEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.accountForm.AccountFormModal

internal class AccountsEntryImpl : AccountsEntry {
    override fun accountFormModal(account: Account?): Modal = AccountFormModal(account)
}
