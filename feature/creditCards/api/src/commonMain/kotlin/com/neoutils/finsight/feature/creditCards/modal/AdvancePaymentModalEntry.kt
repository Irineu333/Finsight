package com.neoutils.finsight.feature.creditCards.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface AdvancePaymentModalEntry {
    fun create(invoiceId: Long): ModalBottomSheet
}
