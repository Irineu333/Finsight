@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.currencies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currencies_add
import com.neoutils.finsight.resources.currencies_archive_confirm_action
import com.neoutils.finsight.resources.currencies_archive_confirm_message
import com.neoutils.finsight.resources.currencies_archive_confirm_title
import com.neoutils.finsight.resources.currencies_archived_label
import com.neoutils.finsight.resources.currencies_delete_confirm_action
import com.neoutils.finsight.resources.currencies_delete_confirm_message
import com.neoutils.finsight.resources.currencies_delete_confirm_message_rates
import com.neoutils.finsight.resources.currencies_delete_confirm_title
import com.neoutils.finsight.resources.currencies_screen_title
import com.neoutils.finsight.resources.currencies_unarchive_confirm_action
import com.neoutils.finsight.resources.currencies_unarchive_confirm_message
import com.neoutils.finsight.resources.currencies_unarchive_confirm_title
import com.neoutils.finsight.ui.component.CurrencyGlyph
import com.neoutils.finsight.ui.component.ErrorModal
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.currencyForm.CurrencyFormModal
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The registry of currencies: what the app offers, which is data the user owns.
 *
 * Every row is listed, archived ones included and **marked** — hiding them would put
 * unarchiving out of reach and would make "archived" mean "deleted", which is the one
 * thing it does not mean. A row goes on being perfectly valid wherever it is already
 * used; what archiving withdraws is the offer.
 *
 * Deleting says **what it takes with it**. A rate observation does not block the
 * deletion — it is removed in the same write — so the confirmation states how many will
 * go, rather than destroying them quietly. An account or a budget does block it, and the
 * refusal names which, because that is what the user can act on.
 */
@Composable
fun CurrenciesScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CurrenciesViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current

    LaunchedEffect(Unit) {
        analytics.logScreenView("currencies")
    }

    // A refusal is stated where every refusal of this app is stated, and dismissing it
    // clears it — otherwise the next refusal of the same reason would show nothing.
    val error = uiState.error
    LaunchedEffect(error) {
        if (error != null) {
            modalManager.show(ErrorModal(error))
            viewModel.onAction(CurrenciesAction.DismissError)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.currencies_screen_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
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
        floatingActionButton = {
            FloatingActionButton(onClick = { modalManager.show(CurrencyFormModal()) }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.currencies_add),
                )
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    items(uiState.currencies, key = { it.currency.code }) { item ->
                        CurrencyCard(
                            item = item,
                            onEdit = { modalManager.show(CurrencyFormModal(item.currency)) },
                            onArchive = {
                                modalManager.show(
                                    ConfirmCurrencyModal(
                                        item = item,
                                        kind = ConfirmKind.ARCHIVE,
                                        onConfirm = {
                                            viewModel.onAction(
                                                CurrenciesAction.Archive(item.currency.code)
                                            )
                                        },
                                    )
                                )
                            },
                            onUnarchive = {
                                modalManager.show(
                                    ConfirmCurrencyModal(
                                        item = item,
                                        kind = ConfirmKind.UNARCHIVE,
                                        onConfirm = {
                                            viewModel.onAction(
                                                CurrenciesAction.Unarchive(item.currency.code)
                                            )
                                        },
                                    )
                                )
                            },
                            onDelete = {
                                modalManager.show(
                                    ConfirmCurrencyModal(
                                        item = item,
                                        kind = ConfirmKind.DELETE,
                                        ratesToRemove = {
                                            viewModel.ratesRemovedBy(item.currency.code)
                                        },
                                        onConfirm = {
                                            viewModel.onAction(
                                                CurrenciesAction.Delete(item.currency.code)
                                            )
                                        },
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencyCard(
    item: CurrencyItem,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            CurrencyGlyph(symbol = item.currency.symbol)

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.currency.code,
                        fontWeight = FontWeight.Medium,
                    )

                    // A word and not only a colour: "archived" has to be readable by
                    // somebody who does not read colour, exactly as on a category card.
                    if (item.isArchived) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = stringResource(Res.string.currencies_archived_label),
                                style = typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Text(
                    text = item.label,
                    style = typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            // The base is not archivable, so the action is simply absent rather than
            // offered and always refused.
            if (!item.isBase) {
                IconButton(onClick = if (item.isArchived) onUnarchive else onArchive) {
                    Icon(
                        imageVector = if (item.isArchived) {
                            Icons.Default.Unarchive
                        } else {
                            Icons.Default.Archive
                        },
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = colorScheme.error,
                )
            }
        }
    }
}

private enum class ConfirmKind { ARCHIVE, UNARCHIVE, DELETE }

/**
 * The confirmation of an action over a currency.
 *
 * The deletion's message is the reason this is not a plain string: it has to state **how
 * many** rate observations go with the currency, and that number is a question for the
 * archive, asked before the deletion happens.
 */
private class ConfirmCurrencyModal(
    private val item: CurrencyItem,
    private val kind: ConfirmKind,
    private val ratesToRemove: (suspend () -> Int)? = null,
    private val onConfirm: () -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current
        val rates by produceState(initialValue = 0, kind) {
            value = ratesToRemove?.invoke() ?: 0
        }

        val title = when (kind) {
            ConfirmKind.ARCHIVE ->
                stringResource(Res.string.currencies_archive_confirm_title, item.label)

            ConfirmKind.UNARCHIVE ->
                stringResource(Res.string.currencies_unarchive_confirm_title, item.label)

            ConfirmKind.DELETE ->
                stringResource(Res.string.currencies_delete_confirm_title, item.label)
        }

        val message = when {
            kind == ConfirmKind.ARCHIVE ->
                stringResource(Res.string.currencies_archive_confirm_message)

            kind == ConfirmKind.UNARCHIVE ->
                stringResource(Res.string.currencies_unarchive_confirm_message)

            rates > 0 ->
                stringResource(Res.string.currencies_delete_confirm_message_rates, rates)

            else -> stringResource(Res.string.currencies_delete_confirm_message)
        }

        val action = when (kind) {
            ConfirmKind.ARCHIVE -> stringResource(Res.string.currencies_archive_confirm_action)
            ConfirmKind.UNARCHIVE -> stringResource(Res.string.currencies_unarchive_confirm_action)
            ConfirmKind.DELETE -> stringResource(Res.string.currencies_delete_confirm_action)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onConfirm()
                    modalManager.dismiss(this@ConfirmCurrencyModal)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (kind == ConfirmKind.DELETE) {
                        colorScheme.error
                    } else {
                        colorScheme.primary
                    },
                ),
            ) {
                Text(
                    text = action,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
