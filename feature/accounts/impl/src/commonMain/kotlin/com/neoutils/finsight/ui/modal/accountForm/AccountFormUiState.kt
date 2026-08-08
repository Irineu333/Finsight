package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.Validation

data class AccountFormUiState(
    val name: String = "",
    val selectedIcon: AppIcon = AppIcon.WALLET,
    val validation: Map<AccountField, Validation> = mapOf(),
    val isDefault: Boolean = false,
    // Affordance only: it decides whether the account offers the yield line and the
    // launch path, and enters no sum anywhere (design D2).
    val yieldsInterest: Boolean = false,
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
    val canChangeDefault: Boolean = true,
    /**
     * The currency this account is denominated in — **always shown**, design D23.
     *
     * The account form is the only door a second currency is ever born through, so the
     * row cannot hide itself while there is only one currency: if it did, there would
     * never be a second. Cost accepted: ~60dp of form for a user who will never touch
     * it.
     */
    val currency: String = "",
    /**
     * Decided by the **mode of the form**, not by the state of the account (design
     * D12): a picker while creating, a locked state row while editing, always. There is
     * no third case and no condition to keep correct.
     */
    val canChangeCurrency: Boolean = true,
    val selectableCurrencies: List<CurrencyInfo> = emptyList(),
)

enum class AccountField {
    NAME
}
