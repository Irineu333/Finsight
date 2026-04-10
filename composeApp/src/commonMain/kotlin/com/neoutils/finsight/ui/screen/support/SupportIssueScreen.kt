@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.support

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.SupportMessage
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.util.LocalDateFormats
import org.jetbrains.compose.resources.stringResource
import com.neoutils.finsight.domain.analytics.Analytics
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SupportIssueScreen(
    issueId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: SupportIssueViewModel = koinViewModel {
        parametersOf(issueId)
    },
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        analytics.logScreenView("support_issue")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        bottomBar = {
            when (val content = uiState) {
                is SupportIssueUiState.Content -> {
                    ReplyComposer(
                        value = content.replyText,
                        onValueChange = viewModel::onReplyTextChange,
                        onSend = viewModel::sendReply,
                        enabled = content.canSend,
                    )
                }

                else -> Unit
            }
        },
    ) { paddingValues ->
        when (val state = uiState) {
            SupportIssueUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is SupportIssueUiState.Content -> {
                val dateFormats = LocalDateFormats.current
                val today = stringResource(Res.string.support_chat_divider_today)
                val yesterday = stringResource(Res.string.support_chat_divider_yesterday)
                val listState = rememberLazyListState()

                LaunchedEffect(state.messages.size) {
                    if (state.messages.isNotEmpty()) {
                        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                focusManager.clearFocus(force = true)
                            }
                        },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "header") {
                        SupportIssueCard(
                            issue = state.issue,
                            descriptionMaxLines = Int.MAX_VALUE,
                        )
                    }

                    if (state.messages.isEmpty()) {
                        item(key = "messages_empty") {
                            EmptyMessagesState()
                        }
                    } else {
                        itemsIndexed(
                            items = state.messages,
                            key = { _, message -> message.id },
                        ) { index, message ->
                            val isNewDay = index == 0 ||
                                    dateFormats.toLocalDate(state.messages[index - 1].createdAt) !=
                                    dateFormats.toLocalDate(message.createdAt)

                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (isNewDay) {
                                    DateDivider(
                                        label = dateFormats.formatDividerDate(
                                            instant = message.createdAt,
                                            today = today,
                                            yesterday = yesterday,
                                        )
                                    )
                                }
                                MessageBubble(message = message)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: SupportMessage,
    modifier: Modifier = Modifier,
) {
    val dateFormats = LocalDateFormats.current
    val bubbleColor = when (message.author) {
        SupportMessage.Author.USER -> MaterialTheme.colorScheme.primary
        SupportMessage.Author.SUPPORT -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when (message.author) {
        SupportMessage.Author.USER -> MaterialTheme.colorScheme.onPrimary
        SupportMessage.Author.SUPPORT -> MaterialTheme.colorScheme.onSurface
    }
    val bubbleShape = when (message.author) {
        SupportMessage.Author.USER -> RoundedCornerShape(16.dp, 0.dp, 16.dp, 16.dp)
        SupportMessage.Author.SUPPORT -> RoundedCornerShape(0.dp, 16.dp, 16.dp, 16.dp)
    }
    val alignment = when (message.author) {
        SupportMessage.Author.USER -> Alignment.End
        SupportMessage.Author.SUPPORT -> Alignment.Start
    }
    val padding = when (message.author) {
        SupportMessage.Author.USER -> PaddingValues(start = 64.dp)
        SupportMessage.Author.SUPPORT -> PaddingValues(end = 64.dp)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(padding)
                .background(color = bubbleColor, shape = bubbleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = message.body,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (message.isPending && message.author == SupportMessage.Author.USER) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }

            Text(
                text = dateFormats.formatInstantTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateDivider(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        )
    }
}

@Composable
private fun EmptyMessagesState(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = stringResource(Res.string.support_messages_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.support_messages_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReplyComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(text = stringResource(Res.string.support_reply_message_label))
            },
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(
                    onClick = onSend,
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(Res.string.support_reply_send),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            modifier = Modifier
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding()
                .fillMaxWidth(),
            minLines = 1,
            maxLines = 4,
        )
    }
}
