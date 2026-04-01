@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.dashboardComponentOptions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsSegmented
import com.alorma.compose.settings.ui.SettingsSwitch
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.dashboard.*
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource

internal class DashboardComponentOptionsModal(
    private val item: DashboardEditItem,
    private val accounts: List<Account>,
    private val creditCards: List<CreditCard>,
    private val onAction: (DashboardAction) -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        var config by remember { mutableStateOf(item.config) }

        fun updateConfig(newConfig: Map<String, String>) {
            config = newConfig
            onAction(DashboardAction.UpdateComponentConfig(item.key, newConfig))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringUiText(item.title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )

            val topSpacing = config[DashboardComponentConfig.TOP_SPACING] == "true"
            val showHeader = config.showHeader()

            SettingsGroup(
                title = { Text(stringResource(Res.string.component_config_layout_section)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                when (item.key) {
                    DashboardComponent.AccountsOverview.KEY,
                    DashboardComponent.CreditCardsPager.KEY,
                    DashboardComponent.PendingRecurring.KEY,
                    DashboardComponent.Recents.KEY,
                    DashboardComponent.QuickActions.KEY -> {
                        SettingsSwitch(
                            state = showHeader,
                            title = { Text(stringResource(Res.string.component_config_show_header)) },
                            icon = { Icon(Icons.Rounded.ViewHeadline, contentDescription = null) },
                            onCheckedChange = { enabled ->
                                updateConfig(config.toMutableMap().apply {
                                    put(DashboardComponentConfig.SHOW_HEADER, enabled.toString())
                                })
                            },
                        )
                    }
                }

                SettingsSwitch(
                    state = topSpacing,
                    title = { Text(stringResource(Res.string.component_config_top_spacing)) },
                    icon = { Icon(Icons.Rounded.SpaceBar, contentDescription = null) },
                    onCheckedChange = { enabled ->
                        updateConfig(config.toMutableMap().apply {
                            put(DashboardComponentConfig.TOP_SPACING, enabled.toString())
                        })
                    },
                )
            }

            when (item.key) {
                DashboardComponent.ConcreteBalanceStats.KEY -> BalanceStatsContentGroup(
                    config = config,
                    defaultHideWhenEmpty = false,
                    onConfigChange = ::updateConfig,
                )

                DashboardComponent.PendingBalanceStats.KEY,
                DashboardComponent.CreditCardBalanceStats.KEY -> BalanceStatsContentGroup(
                    config = config,
                    defaultHideWhenEmpty = true,
                    onConfigChange = ::updateConfig,
                )

                DashboardComponent.AccountsOverview.KEY -> AccountsOverviewContentGroup(
                    accounts = accounts,
                    config = config,
                    onConfigChange = ::updateConfig,
                )

                DashboardComponent.CreditCardsPager.KEY -> CreditCardsPagerContentGroup(
                    creditCards = creditCards,
                    config = config,
                    onConfigChange = ::updateConfig,
                )

                DashboardComponent.SpendingByCategory.KEY -> SpendingByCategoryContentGroup(
                    config = config,
                    onConfigChange = ::updateConfig,
                )

                DashboardComponent.PendingRecurring.KEY -> PendingRecurringContentGroup(
                    config = config,
                    onConfigChange = ::updateConfig,
                )

                DashboardComponent.Recents.KEY -> RecentsContentGroup(
                    config = config,
                    onConfigChange = ::updateConfig,
                )

                DashboardComponent.QuickActions.KEY -> QuickActionsContentGroup(
                    config = config,
                    onConfigChange = ::updateConfig,
                )
            }
        }
    }
}

@Composable
private fun BalanceStatsContentGroup(
    config: Map<String, String>,
    defaultHideWhenEmpty: Boolean,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    SettingsGroup(
        title = { Text(stringResource(Res.string.component_config_content_section)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingsSwitch(
            state = config.hideWhenEmpty(defaultValue = defaultHideWhenEmpty),
            title = { Text(stringResource(Res.string.component_config_hide_when_empty)) },
            icon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
            onCheckedChange = { enabled ->
                onConfigChange(config.toMutableMap().apply {
                    put(DashboardComponentConfig.HIDE_WHEN_EMPTY, enabled.toString())
                })
            },
        )
    }
}

@Composable
private fun AccountsOverviewContentGroup(
    accounts: List<Account>,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val hideSingleAccount = config[AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT] != "false"
    val excludedIds = config[AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS]
        ?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toLongOrNull() }?.toSet()
        ?: emptySet()

    SettingsGroup(
        title = { Text(stringResource(Res.string.component_config_content_section)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingsSwitch(
            state = hideSingleAccount,
            title = { Text(stringResource(Res.string.component_config_hide_single_account)) },
            icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
            onCheckedChange = { enabled ->
                onConfigChange(config.toMutableMap().apply {
                    put(AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT, enabled.toString())
                })
            },
        )

        accounts.forEach { account ->
            SettingsSwitch(
                state = account.id !in excludedIds,
                title = { Text(account.name) },
                onCheckedChange = { checked ->
                    val newExcluded = if (checked) excludedIds - account.id else excludedIds + account.id
                    onConfigChange(config.toMutableMap().apply {
                        put(AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS, newExcluded.joinToString(","))
                    })
                },
            )
        }
    }
}

@Composable
private fun CreditCardsPagerContentGroup(
    creditCards: List<CreditCard>,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val showEmptyState = config[DashboardComponentConfig.SHOW_EMPTY_STATE] == "true"
    val excludedIds = config[CreditCardsPagerConfig.EXCLUDED_CARD_IDS]
        ?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toLongOrNull() }?.toSet()
        ?: emptySet()

    SettingsGroup(
        title = { Text(stringResource(Res.string.component_config_content_section)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingsSwitch(
            state = showEmptyState,
            title = { Text(stringResource(Res.string.component_config_show_empty_state)) },
            icon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
            onCheckedChange = { enabled ->
                onConfigChange(config.toMutableMap().apply {
                    put(DashboardComponentConfig.SHOW_EMPTY_STATE, enabled.toString())
                })
            },
        )

        creditCards.forEach { card ->
            SettingsSwitch(
                state = card.id !in excludedIds,
                title = { Text(card.name) },
                onCheckedChange = { checked ->
                    val newExcluded = if (checked) excludedIds - card.id else excludedIds + card.id
                    onConfigChange(config.toMutableMap().apply {
                        put(CreditCardsPagerConfig.EXCLUDED_CARD_IDS, newExcluded.joinToString(","))
                    })
                },
            )
        }
    }
}

@Composable
private fun SpendingByCategoryContentGroup(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val options = listOf(3, 5, 10, -1)
    val current = config[SpendingByCategoryConfig.MAX_CATEGORIES]?.toIntOrNull() ?: -1
    val allLabel = stringResource(Res.string.component_config_all)

    SettingsGroup(
        title = { Text(stringResource(Res.string.component_config_content_section)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingsSegmented(
            title = { Text(stringResource(Res.string.component_config_max_categories)) },
            items = options,
            selectedItem = current,
            itemTitleMap = { if (it == -1) allLabel else it.toString() },
            onItemSelected = { value ->
                onConfigChange(config.toMutableMap().apply {
                    put(SpendingByCategoryConfig.MAX_CATEGORIES, value.toString())
                })
            },
            buttonShape = { index ->
                SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            },
            buttonIcon = {},
        )
    }
}

@Composable
private fun PendingRecurringContentGroup(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val options = listOf(0, 7, 15, 30)
    val current = config[PendingRecurringConfig.UPCOMING_DAYS_AHEAD]?.toIntOrNull()
        ?: PendingRecurringConfig.DEFAULT_UPCOMING_DAYS_AHEAD
    val todayLabel = stringResource(Res.string.component_config_today)
    val sevenDaysLabel = stringResource(Res.string.component_config_7_days)
    val fifteenDaysLabel = stringResource(Res.string.component_config_15_days)
    val thisMonthLabel = stringResource(Res.string.component_config_this_month)

    SettingsGroup(
        title = { Text(stringResource(Res.string.component_config_content_section)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingsSegmented(
            title = { Text(stringResource(Res.string.component_config_days_ahead)) },
            items = options,
            selectedItem = current,
            itemTitleMap = { value ->
                when (value) {
                    0 -> todayLabel
                    7 -> sevenDaysLabel
                    15 -> fifteenDaysLabel
                    else -> thisMonthLabel
                }
            },
            onItemSelected = { value ->
                onConfigChange(config.toMutableMap().apply {
                    put(PendingRecurringConfig.UPCOMING_DAYS_AHEAD, value.toString())
                })
            },
            buttonShape = { index ->
                SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            },
            buttonIcon = {},
        )
    }
}

@Composable
private fun RecentsContentGroup(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val options = listOf(4, 6, 8, 10)
    val current = config[RecentsConfig.COUNT]?.toIntOrNull() ?: RecentsConfig.DEFAULT_COUNT

    SettingsGroup(
        title = { Text(stringResource(Res.string.component_config_content_section)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingsSegmented(
            title = { Text(stringResource(Res.string.component_config_count)) },
            items = options,
            selectedItem = current,
            itemTitleMap = { it.toString() },
            onItemSelected = { value ->
                onConfigChange(config.toMutableMap().apply {
                    put(RecentsConfig.COUNT, value.toString())
                })
            },
            buttonShape = { index ->
                SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            },
            buttonIcon = {},
        )
    }
}

@Composable
private fun QuickActionsContentGroup(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val hiddenActions = config[QuickActionsConfig.HIDDEN_ACTIONS]
        ?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    val visibleCount = QuickActionType.entries.count { it.name !in hiddenActions }

    SettingsGroup(
        title = { Text(stringResource(Res.string.component_config_content_section)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        QuickActionType.entries.forEach { action ->
            val isVisible = action.name !in hiddenActions
            val canToggle = !isVisible || visibleCount > 1

            SettingsSwitch(
                state = isVisible,
                enabled = canToggle,
                title = { Text(stringUiText(action.title)) },
                subtitle = if (!canToggle) {
                    { Text(stringResource(Res.string.component_config_min_visible_action)) }
                } else {
                    null
                },
                onCheckedChange = { checked ->
                    val newHidden = if (checked) hiddenActions - action.name else hiddenActions + action.name
                    onConfigChange(config.toMutableMap().apply {
                        put(QuickActionsConfig.HIDDEN_ACTIONS, newHidden.joinToString(","))
                    })
                },
            )
        }
    }
}
