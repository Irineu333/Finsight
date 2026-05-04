package com.neoutils.finsight.feature.accounts.modal.accountForm

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface AccountFormModalEntry {
    fun create(account: Account? = null): ModalBottomSheet
}
