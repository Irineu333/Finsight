@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.screen.creditCards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.ui.component.CreditCardUI
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.LocalNavigator
import com.neoutils.finance.ui.component.NavigationAction
import com.neoutils.finance.ui.modal.addCreditCard.AddCreditCardModal
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreditCardsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CreditCardsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CreditCardsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun CreditCardsContent(
    uiState: CreditCardsUiState,
    onNavigateBack: () -> Unit
) {
    val modalManager = LocalModalManager.current
    val navigator = LocalNavigator.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Cartões de Crédito")
                },
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
            FloatingActionButton(
                onClick = {
                    modalManager.show(AddCreditCardModal())
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = uiState.creditCards,
                key = { it.creditCard.id }
            ) { creditCardUi ->
                CreditCardUI(
                    ui = creditCardUi,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    onClick = {
                        navigator.navigate(
                            NavigationAction.InvoiceTransactions(creditCardUi.creditCard.id)
                        )
                    }
                )
            }
        }
    }
}

