package com.neoutils.finsight.feature.accounts.api

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.ui.component.Modal

interface AccountsEntry {
    fun accountFormModal(account: Account? = null): Modal
}
