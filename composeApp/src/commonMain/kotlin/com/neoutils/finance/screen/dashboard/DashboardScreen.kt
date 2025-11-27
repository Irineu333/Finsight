package com.neoutils.finance.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.component.TransactionCard
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(
    onAddTransaction: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val transactions by viewModel.recentTransactions.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transações Recentes",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    TextButton(
                        onClick = {
                            // TODO: open transactions screen
                        }
                    ) {
                        Text(text = "Ver Tudo")
                    }
                }
            }

            items(
                items = transactions,
                key = { it.id },
            ) { transaction ->
                TransactionCard(transaction = transaction)
            }
        }
    }
}