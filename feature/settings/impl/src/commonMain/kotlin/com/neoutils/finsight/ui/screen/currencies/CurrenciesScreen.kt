@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.currencies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.shell.api.ChromeAction
import com.neoutils.finsight.feature.shell.api.ChromeEffect
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currencies_add
import com.neoutils.finsight.resources.currencies_archived_label
import com.neoutils.finsight.resources.currencies_screen_title
import com.neoutils.finsight.ui.component.CurrencyGlyph
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.currencyForm.CurrencyFormModal
import com.neoutils.finsight.ui.modal.viewCurrency.ViewCurrencyModal
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
 * **A row carries no action.** It opens `ViewCurrencyModal`, which is where editing,
 * archiving and deleting live — the same step an account, a card and a category all
 * take. Buttons inside the rows of a vertical list turn every row into a small toolbar
 * and put a destructive action one mis-tap away from a scroll.
 */
@Composable
fun CurrenciesScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CurrenciesViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current
    val detailController = LocalDetailPaneController.current

    LaunchedEffect(Unit) {
        analytics.logScreenView("currencies")
    }

    // The one action of this screen had no test tag at all while it was a `Scaffold` slot: the
    // only screen where automation could not reach the create button. Publishing it is where the
    // id gets declared, so it comes along.
    ChromeEffect(
        actions = remember(modalManager) {
            listOf(
                ChromeAction(
                    icon = Icons.Default.Add,
                    labelRes = Res.string.currencies_add,
                    testTag = "currencies_add",
                    onClick = { modalManager.show(CurrencyFormModal()) },
                )
            )
        }
    )

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
                            onClick = {
                                detailController.show(ViewCurrencyModal(item.currency.code))
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
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
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

            // The chevron every openable row of this app wears — what says the row leads
            // somewhere instead of doing something.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
            )
        }
    }
}
