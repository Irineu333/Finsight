@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.dashboardComponentOptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.dashboard.*
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource

class DashboardComponentOptionsModal(
    private val item: DashboardEditItem,
    private val accounts: List<Account>,
    private val creditCards: List<CreditCard>,
    private val onAction: (DashboardAction) -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        var config by remember { mutableStateOf(item.config) }
        val scrollState = rememberScrollState()

        fun updateConfig(newConfig: Map<String, String>) {
            config = newConfig
            onAction(DashboardAction.UpdateComponentConfig(item.key, newConfig))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringUiText(item.title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            DashboardConfigSectionLabel(
                text = stringResource(Res.string.component_config_layout_section),
                modifier = Modifier.fillMaxWidth(),
            )

            val topSpacing = config[DashboardComponentConfig.TOP_SPACING] == "true"
            val showHeader = config.showHeader()
            DashboardConfigCard {
                when (item.key) {
                    DashboardComponentKey.ACCOUNTS_OVERVIEW.value,
                    DashboardComponentKey.CREDIT_CARDS_PAGER.value,
                    DashboardComponentKey.PENDING_RECURRING.value,
                    DashboardComponentKey.RECENTS.value,
                    DashboardComponentKey.QUICK_ACTIONS.value -> {
                        DashboardHeaderVisibilityConfigToggle(
                            checked = showHeader,
                            onCheckedChange = { enabled ->
                                updateConfig(config.toMutableMap().apply {
                                    put(DashboardComponentConfig.SHOW_HEADER, enabled.toString())
                                })
                            },
                        )
                    }
                }

                DashboardTopSpacingConfigToggle(
                    checked = topSpacing,
                    onCheckedChange = { enabled ->
                        updateConfig(config.toMutableMap().apply {
                            put(DashboardComponentConfig.TOP_SPACING, enabled.toString())
                        })
                    },
                )
            }

            when (item.key) {
                DashboardComponentKey.CONCRETE_BALANCE_STATS.value -> {
                    DashboardConfigSectionLabel(
                        text = stringResource(Res.string.component_config_content_section),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    BalanceStatsConfigContent(
                        config = config,
                        defaultHideWhenEmpty = false,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponentKey.PENDING_BALANCE_STATS.value -> {
                    DashboardConfigSectionLabel(
                        text = stringResource(Res.string.component_config_content_section),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    BalanceStatsConfigContent(
                        config = config,
                        defaultHideWhenEmpty = true,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponentKey.CREDIT_CARD_BALANCE_STATS.value -> {
                    DashboardConfigSectionLabel(
                        text = stringResource(Res.string.component_config_content_section),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    BalanceStatsConfigContent(
                        config = config,
                        defaultHideWhenEmpty = true,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponentKey.ACCOUNTS_OVERVIEW.value -> {
                    DashboardConfigSectionLabel(
                        text = stringResource(Res.string.component_config_content_section),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    AccountsOverviewConfigContent(
                        accounts = accounts,
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponentKey.CREDIT_CARDS_PAGER.value -> {
                    DashboardConfigSectionLabel(
                        text = stringResource(Res.string.component_config_content_section),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    CreditCardsPagerConfigContent(
                        creditCards = creditCards,
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponentKey.SPENDING_BY_CATEGORY.value -> {
                    DashboardConfigSectionLabel(
                        text = stringResource(Res.string.component_config_content_section),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SpendingByCategoryConfigContent(
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponentKey.INCOME_BY_CATEGORY.value -> {
                    DashboardConfigSectionLabel(
                        text = stringResource(Res.string.component_config_content_section),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    IncomeByCategoryConfigContent(
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponentKey.PENDING_RECURRING.value -> {
                    DashboardConfigSectionLabel(
                        text = stringResource(Res.string.component_config_content_section),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    PendingRecurringConfigContent(
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponentKey.RECENTS.value -> {
                    DashboardConfigSectionLabel(
                        text = stringResource(Res.string.component_config_content_section),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    RecentsConfigContent(
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponentKey.QUICK_ACTIONS.value -> {
                    DashboardConfigSectionLabel(
                        text = stringResource(Res.string.component_config_content_section),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    QuickActionsConfigContent(
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun DashboardConfigSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun DashboardConfigCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun DashboardHeaderVisibilityConfigToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    DashboardConfigToggleRow(
        title = stringResource(Res.string.component_config_show_header),
        checked = checked,
        onCheckedChange = onCheckedChange,
        leadingIcon = Icons.Rounded.ViewHeadline,
    )
}

@Composable
private fun DashboardTopSpacingConfigToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    DashboardConfigToggleRow(
        title = stringResource(Res.string.component_config_top_spacing),
        checked = checked,
        onCheckedChange = onCheckedChange,
        leadingIcon = Icons.Rounded.SpaceBar,
    )
}

@Composable
private fun DashboardConfigToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    val titleColor = if (enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.6f)
    val supportingColor = if (enabled) {
        colorScheme.onSurfaceVariant
    } else {
        colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            DashboardConfigLeadingIcon(
                icon = leadingIcon,
                enabled = enabled,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                )
            }
        }

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorScheme.primary,
                checkedTrackColor = colorScheme.primary.copy(alpha = 0.35f),
                checkedBorderColor = colorScheme.primary,
                uncheckedThumbColor = colorScheme.onSurfaceVariant,
                uncheckedTrackColor = colorScheme.surfaceVariant,
                uncheckedBorderColor = colorScheme.outline,
                disabledCheckedThumbColor = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                disabledCheckedTrackColor = colorScheme.surfaceVariant,
                disabledCheckedBorderColor = colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
private fun DashboardConfigLeadingIcon(
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = accentColor.copy(alpha = if (enabled) 0.12f else 0.08f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DashboardSegmentedOptionCard(
    title: String,
    options: List<Int>,
    current: Int,
    onOptionSelected: (Int) -> Unit,
    optionLabel: (Int) -> String,
) {
    val segmentedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colorScheme.primary.copy(alpha = 0.2f),
        activeContentColor = colorScheme.primary,
        activeBorderColor = colorScheme.primary,
        inactiveContainerColor = colorScheme.surfaceContainerHighest,
        inactiveContentColor = colorScheme.onSurfaceVariant,
        inactiveBorderColor = colorScheme.outline,
    )

    DashboardConfigCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = current == value,
                        onClick = { onOptionSelected(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        colors = segmentedColors,
                        icon = {},
                    ) {
                        Text(
                            text = optionLabel(value),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceStatsConfigContent(
    config: Map<String, String>,
    defaultHideWhenEmpty: Boolean,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val hideWhenEmpty = config.hideWhenEmpty(defaultValue = defaultHideWhenEmpty)

    DashboardConfigCard {
        DashboardConfigToggleRow(
            title = stringResource(Res.string.component_config_hide_when_empty),
            checked = hideWhenEmpty,
            onCheckedChange = { enabled ->
                onConfigChange(config.toMutableMap().apply {
                    put(DashboardComponentConfig.HIDE_WHEN_EMPTY, enabled.toString())
                })
            },
            leadingIcon = Icons.Default.VisibilityOff,
        )
    }
}

@Composable
private fun AccountsOverviewConfigContent(
    accounts: List<Account>,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val hideSingleAccount = config[AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT] != "false"
    val excludedIds = config[AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS]
        ?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toLongOrNull() }?.toSet()
        ?: emptySet()

    DashboardConfigCard {
        DashboardConfigToggleRow(
            title = stringResource(Res.string.component_config_hide_single_account),
            checked = hideSingleAccount,
            onCheckedChange = { enabled ->
                onConfigChange(config.toMutableMap().apply {
                    put(AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT, enabled.toString())
                })
            },
            leadingIcon = Icons.Default.AccountBalanceWallet,
        )

        Spacer(modifier = Modifier.height(12.dp))

        accounts.forEach { account ->
            val included = account.id !in excludedIds
            DashboardConfigToggleRow(
                title = account.name,
                checked = included,
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
private fun CreditCardsPagerConfigContent(
    creditCards: List<CreditCard>,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val showEmptyState = config[DashboardComponentConfig.SHOW_EMPTY_STATE] == "true"
    val excludedIds = config[CreditCardsPagerConfig.EXCLUDED_CARD_IDS]
        ?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toLongOrNull() }?.toSet()
        ?: emptySet()

    DashboardConfigCard {
        DashboardConfigToggleRow(
            title = stringResource(Res.string.component_config_show_empty_state),
            checked = showEmptyState,
            onCheckedChange = { enabled ->
                onConfigChange(config.toMutableMap().apply {
                    put(DashboardComponentConfig.SHOW_EMPTY_STATE, enabled.toString())
                })
            },
            leadingIcon = Icons.Default.CreditCard,
        )

        creditCards.forEach { card ->
            val included = card.id !in excludedIds
            DashboardConfigToggleRow(
                title = card.name,
                checked = included,
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
private fun SpendingByCategoryConfigContent(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val options = listOf(3, 5, 10, -1)
    val current = config[SpendingByCategoryConfig.MAX_CATEGORIES]?.toIntOrNull() ?: -1
    val allLabel = stringResource(Res.string.component_config_all)

    DashboardSegmentedOptionCard(
        title = stringResource(Res.string.component_config_max_categories),
        options = options,
        current = current,
        onOptionSelected = { value ->
            onConfigChange(config.toMutableMap().apply {
                put(SpendingByCategoryConfig.MAX_CATEGORIES, value.toString())
            })
        },
        optionLabel = { value ->
            if (value == -1) {
                allLabel
            } else {
                value.toString()
            }
        },
    )
}

@Composable
private fun IncomeByCategoryConfigContent(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val options = listOf(3, 5, 10, -1)
    val current = config[IncomeByCategoryConfig.MAX_CATEGORIES]?.toIntOrNull() ?: -1
    val allLabel = stringResource(Res.string.component_config_all)

    DashboardSegmentedOptionCard(
        title = stringResource(Res.string.component_config_max_categories),
        options = options,
        current = current,
        onOptionSelected = { value ->
            onConfigChange(config.toMutableMap().apply {
                put(IncomeByCategoryConfig.MAX_CATEGORIES, value.toString())
            })
        },
        optionLabel = { value ->
            if (value == -1) {
                allLabel
            } else {
                value.toString()
            }
        },
    )
}

@Composable
private fun PendingRecurringConfigContent(
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

    DashboardSegmentedOptionCard(
        title = stringResource(Res.string.component_config_days_ahead),
        options = options,
        current = current,
        onOptionSelected = { value ->
            onConfigChange(config.toMutableMap().apply {
                put(PendingRecurringConfig.UPCOMING_DAYS_AHEAD, value.toString())
            })
        },
        optionLabel = { value ->
            when (value) {
                0 -> todayLabel
                7 -> sevenDaysLabel
                15 -> fifteenDaysLabel
                else -> thisMonthLabel
            }
        },
    )
}

@Composable
private fun RecentsConfigContent(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val options = listOf(4, 6, 8, 10)
    val current = config[RecentsConfig.COUNT]?.toIntOrNull() ?: RecentsConfig.DEFAULT_COUNT

    DashboardSegmentedOptionCard(
        title = stringResource(Res.string.component_config_count),
        options = options,
        current = current,
        onOptionSelected = { value ->
            onConfigChange(config.toMutableMap().apply {
                put(RecentsConfig.COUNT, value.toString())
            })
        },
        optionLabel = { value -> value.toString() },
    )
}

@Composable
private fun QuickActionsConfigContent(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val hiddenActions = config[QuickActionsConfig.HIDDEN_ACTIONS]
        ?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    val visibleCount = QuickActionType.entries.count { it.name !in hiddenActions }

    DashboardConfigCard {
        QuickActionType.entries.forEach { action ->
            val isVisible = action.name !in hiddenActions
            val canToggle = !isVisible || visibleCount > 1

            DashboardConfigToggleRow(
                title = stringUiText(action.title),
                checked = isVisible,
                enabled = canToggle,
                supportingText = if (!canToggle) {
                    stringResource(Res.string.component_config_min_visible_action)
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
