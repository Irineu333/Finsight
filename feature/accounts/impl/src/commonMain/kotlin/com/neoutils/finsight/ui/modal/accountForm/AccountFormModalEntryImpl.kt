package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.ui.component.ModalBottomSheet

class AccountFormModalEntryImpl : AccountFormModalEntry {
    override fun create(account: Account?): ModalBottomSheet =
        AccountFormModal(account = account)
}
