package com.neoutils.finsight.ui.screen.settings

sealed interface SettingsAction {

    /**
     * Switches the base currency. It writes the preference and nothing else — no
     * confirmation, no coverage check, no rate demanded in the flow (design D6).
     */
    data class SwitchBaseCurrency(val code: String) : SettingsAction
}
