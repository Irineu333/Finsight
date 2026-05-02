package com.neoutils.finsight.feature.accounts.modal.accountForm

import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
class AccountFormModalEntryImpl : AccountFormModalEntry {
    override fun create(account: Account?): ModalBottomSheet =
        AccountFormModal(account = account)
}
