package com.neoutils.finsight.feature.accounts.modal.accountForm

import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface AccountFormModalEntry {
    fun create(accountId: Long? = null): ModalBottomSheet
}
