package com.neoutils.finsight.feature.accounts.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.accounts.modal.accountForm.AccountFormModal
import com.neoutils.finsight.feature.accounts.modal.accountForm.AccountFormModalEntry

class AccountFormModalEntryImpl : AccountFormModalEntry {
    override fun create(accountId: Long?): ModalBottomSheet =
        AccountFormModal(accountId = accountId)
}
