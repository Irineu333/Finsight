@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.archived

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.credit_cards_archived_empty
import com.neoutils.finsight.resources.credit_cards_archived_title
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.modal.viewCreditCard.ViewCreditCardModal
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ArchivedCreditCardsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ArchivedCreditCardsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArchivedCreditCardsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
private fun ArchivedCreditCardsContent(
    uiState: ArchivedCreditCardsUiState,
    onNavigateBack: () -> Unit,
) {
    val detailController = LocalDetailPaneController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.credit_cards_archived_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                ),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        when (uiState) {
            ArchivedCreditCardsUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            ArchivedCreditCardsUiState.Empty -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.credit_cards_archived_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            is ArchivedCreditCardsUiState.Content -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = uiState.creditCards,
                    key = { it.cardId },
                ) { creditCard ->
                    ArchivedCreditCardCard(
                        creditCard = creditCard,
                        onClick = { detailController.show(ViewCreditCardModal(creditCard.cardId)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
