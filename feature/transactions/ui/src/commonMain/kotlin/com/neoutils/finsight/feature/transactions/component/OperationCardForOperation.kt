package com.neoutils.finsight.feature.transactions.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
import com.neoutils.finsight.feature.transactions.mapper.IOperationUiMapper
import com.neoutils.finsight.feature.transactions.model.OperationUi
import org.koin.compose.koinInject

@Composable
fun OperationCard(
    operation: Operation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    amountDecoration: TextDecoration = TextDecoration.None,
    perspective: OperationPerspective = OperationPerspective.Account(accountId = 0L),
) {
    val mapper = koinInject<IOperationUiMapper>()
    val operationUi by produceState<OperationUi?>(initialValue = null, operation.id, perspective) {
        value = mapper.toUi(operation, perspective)
    }
    val ui = operationUi ?: return
    OperationCard(
        operationUi = ui,
        onClick = onClick,
        modifier = modifier,
        amountDecoration = amountDecoration,
    )
}
