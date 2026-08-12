package com.neoutils.finsight.ui.modal.createInvoice

import com.neoutils.finsight.domain.model.InvoiceMonthSelection

data class CreateInvoiceUiState(
    val selection: InvoiceMonthSelection,
    /**
     * Whether the card's invoices have been read. Before that the sheet knows the month it
     * opened on but not whether it is taken, and an enabled button would be a claim it
     * cannot yet make.
     */
    val isLoaded: Boolean = false,
) {
    /**
     * The window the created invoice will be given — the selection's own, derived from the
     * card. What the sheet shows before creating and what gets written are the same value
     * because they are the same expression.
     */
    val window = selection.window

    /**
     * Whether the month is still free. An occupied month stays visible and navigable and
     * only the submission goes away — the domain refuses it as well, so this is the
     * courtesy and not the guard.
     */
    val canSubmit = isLoaded && selection.isNew
}
