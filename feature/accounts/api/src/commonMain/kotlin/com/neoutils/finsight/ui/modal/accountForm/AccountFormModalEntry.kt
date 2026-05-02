package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.ui.component.ModalBottomSheet

interface AccountFormModalEntry {
    fun create(account: Account? = null): ModalBottomSheet
}
