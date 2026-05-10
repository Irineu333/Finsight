package com.neoutils.finsight.feature.creditCards.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.creditCards.modal.EditInvoiceBalanceModalEntry
import com.neoutils.finsight.feature.creditCards.modal.editInvoiceBalance.EditInvoiceBalanceModal

class EditInvoiceBalanceModalEntryImpl : EditInvoiceBalanceModalEntry {
    override fun create(invoiceId: Long): ModalBottomSheet =
        EditInvoiceBalanceModal(invoiceId = invoiceId)
}
