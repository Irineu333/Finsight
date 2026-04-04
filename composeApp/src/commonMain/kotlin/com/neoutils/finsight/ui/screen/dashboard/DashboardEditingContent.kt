@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.dashboardComponentOptions.DashboardComponentOptionsModal
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun DashboardEditingContent(
    state: DashboardUiState.Editing,
    onAction: (DashboardAction) -> Unit,
) {
    val modalManager = LocalModalManager.current
    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->

        val fromKey = when (from.key) {
            EditListEntry.ActivePlaceholder.key,
            EditListEntry.SectionHeader.key,
            EditListEntry.AvailablePlaceholder.key -> return@rememberReorderableLazyListState

            else -> from.key.toString()
        }

        onAction(
            DashboardAction.MoveComponent(
                fromKey = fromKey,
                toKey = to.key.toString(),
            )
        )

        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val entries = rememberDashboardEditListEntries(state)

    LazyColumn(
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 32.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = entries,
            key = { it.key }
        ) { entry ->
            ReorderableItem(
                state = reorderState,
                key = entry.key,
            ) {
                when (entry) {
                    is EditListEntry.Component -> {
                        DashboardEditItemWrapper(
                            item = entry.item,
                            onTap = {
                                if (entry.isActive) {
                                    modalManager.show(
                                        DashboardComponentOptionsModal(
                                            item = entry.item,
                                            accounts = state.accounts,
                                            creditCards = state.creditCards,
                                            onAction = onAction,
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.alpha(alpha = if (entry.isActive) 1f else 0.6f),
                        )
                    }

                    EditListEntry.ActivePlaceholder -> {
                        DashboardEditPlaceholder(
                            text = stringResource(Res.string.dashboard_edit_active_placeholder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }

                    EditListEntry.SectionHeader -> {
                        Text(
                            text = stringResource(Res.string.dashboard_edit_available_section),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    EditListEntry.AvailablePlaceholder -> {
                        DashboardEditPlaceholder(
                            text = stringResource(Res.string.dashboard_edit_available_placeholder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.DashboardEditItemWrapper(
    item: DashboardEditItem,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringUiText(item.title),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                DashboardComponentContent(
                    variant = item.preview,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Global Overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onTap)
                    .longPressDraggableHandle(
                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                        onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                    ),
            )
        }
    }
}

@Composable
private fun DashboardEditPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
) {
    val borderColor = colorScheme.outlineVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(80.dp)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
